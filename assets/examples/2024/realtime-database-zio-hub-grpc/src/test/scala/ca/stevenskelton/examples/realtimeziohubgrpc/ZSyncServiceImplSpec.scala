package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.ETag
import ca.stevenskelton.examples.realtimeziohubgrpc.ZSyncServiceImplSpec.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.DataUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse}
import zio.stream.{UStream, ZStream}
import zio.test.assertTrue
import zio.test.junit.JUnitRunnableSpec

class ZSyncServiceImplSpec extends JUnitRunnableSpec {
  
  /**
   * Client 1: watch 1
   * Client 2: watch 1,2
   * Client 3: watch 1,3
   */
  val SubscribeActions: Seq[(UserId, SyncRequest)] = Seq(
    (1, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1)))),
    (2, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1), SyncRequest.Subscribe(id = 2)))),
    (3, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1), SyncRequest.Subscribe(id = 3)))),
  )

  override def spec = suite("multiple client listeners")(
    test("All updated") {
      for
        client <- BidirectionalTestClients.launch

        responses <- client.responses(15, SubscribeActions ++ Seq(
          (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(1))),
          (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
          (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(3))),
        ))

        user1_id1_size = responses.idRecords(1, userId = 1).size

        user2_id1_size = responses.idRecords(1, userId = 2).size
        user2_id2_size = responses.idRecords(2, userId = 2).size

        user3_id1_size = responses.idRecords(1, userId = 3).size
        user3_id3_size = responses.idRecords(3, userId = 3).size

      yield

        //Client 1
        val client1Responses = responses.withFilter(_._1 == 1).map(_._2)
        client1Responses.foreach(o => println(o.toString))

        //Client 2
        val client2Responses = responses.withFilter(_._1 == 2).map(_._2)
        client2Responses.foreach(o => println(o.toString))

        //Client 3
        val client3Responses = responses.withFilter(_._1 == 3).map(_._2)
        client3Responses.foreach(o => println(o.toString))

        assertTrue:
          responses.size == 15 &&
            user1_id1_size == 3 &&
            user2_id1_size == 3 &&
            user2_id2_size == 3 &&
            user3_id1_size == 3 &&
            user3_id3_size == 3
    },
    test("Unsubscribe by Id") {
      for
        client <- BidirectionalTestClients.launch
        responses0 <- client.responses(5, SubscribeActions :+ (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(1))))
        responses1 <- client.responses(8, Seq(
          (2, SyncRequest(unsubscribeIds = Seq(1))),
          (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
          (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(3))),
        ))
      yield assertTrue:
        responses0.length == 5 &&
          responses0.idRecords(1, userId = 1).size == 1 &&
          responses0.idRecords(1, userId = 2).size == 1 &&
          responses0.idRecords(2, userId = 2).size == 1 &&
          responses0.idRecords(1, userId = 3).size == 1 &&
          responses0.idRecords(3, userId = 3).size == 1 &&
          responses1.length == 8 &&
          responses1.idRecords(1, userId = 1).size == 2 &&
          responses1.idRecords(1, userId = 2).isEmpty &&
          responses1.idRecords(2, userId = 2).size == 2 &&
          responses1.idRecords(1, userId = 3).size == 2 &&
          responses1.idRecords(3, userId = 3).size == 2
    },
    test("Unsubscribe All") {
      val streamActions = Seq(
        (2, SyncRequest(unsubscribeAll = true)),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(3))),
      )
      for
        client <- BidirectionalTestClients.launch
        _ <- client.responses(5, SubscribeActions :+ (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(1))))
        responses <- client.responses(6, streamActions)
      yield assertTrue:
        responses.length == 6 &&
          responses.idRecords(1, userId = 1).size == 2 &&
          responses.idRecords(1, userId = 2).isEmpty &&
          responses.idRecords(2, userId = 2).isEmpty &&
          responses.idRecords(1, userId = 3).size == 2 &&
          responses.idRecords(3, userId = 3).size == 2
    },
    test("Limit Subscribe Response when previous_etag matches") {
      val initialData = ZSyncServiceImplSpec.createDataUpdates(1)
      val streamActions = Seq(
        (1, SyncRequest(updates = initialData)),
        (1, SyncRequest(subscribes = Seq(
          SyncRequest.Subscribe.of(id = 1, previousEtag = initialData.etag(1)),
          SyncRequest.Subscribe(id = 2),
        ))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
      )
      for
        client <- BidirectionalTestClients.launch
        responses <- client.responses(3, streamActions)
      yield assertTrue:
        responses.length == 3 &&
          responses.idRecords(1).size == 1 &&
          responses.idRecords(2).size == 2
    }
  ) @@ zio.test.TestAspect.sequential //@@ zio.test.TestAspect.timeout(5.seconds)
}

object ZSyncServiceImplSpec {

  extension (userResponses: Seq[(UserId, SyncResponse)])
    def idRecords(id: Int, userId: UserId = 1): Seq[Data] = userResponses.filter {
      t => t._1 == userId && t._2.data.get.id == id
    }.flatMap(_._2.data)

  extension (dataUpdates: Seq[DataUpdate])
    def etag(id: Int): ETag = DataRecord.calculateEtag(dataUpdates.find(_.data.exists(_.id == id)).get.data.get)

  extension (streamActions: Seq[(UserId, SyncRequest)])
    def stream: UStream[(UserId, SyncRequest)] = ZStream.fromIterable(streamActions, 1) //.throttleShape(1, 1.milliseconds)(_.size)

  private def createData(batch: Int): Seq[Data] = Seq(
    Data.of(id = 1, field1 = s"batch-$batch"),
    Data.of(id = 2, field1 = s"batch-$batch"),
    Data.of(id = 3, field1 = s"batch-$batch"),
    Data.of(id = 4, field1 = s"batch-$batch"),
    Data.of(id = 5, field1 = s"batch-$batch"),
  )

  def createDataUpdates(batch: Int): Seq[DataUpdate] = createData(batch).zip(createData(batch - 1)).map:
    (data, previousData) => DataUpdate.of(Some(data), DataRecord.calculateEtag(previousData))

}

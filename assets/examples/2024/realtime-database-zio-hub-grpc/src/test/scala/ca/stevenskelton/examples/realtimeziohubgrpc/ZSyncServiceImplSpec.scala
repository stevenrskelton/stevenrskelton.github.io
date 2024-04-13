package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.ETag
import ca.stevenskelton.examples.realtimeziohubgrpc.ZSyncServiceImplSpec.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateRequest.DataUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, UpdateRequest}
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
        clients <- BidirectionalTestClients.launch
        _ <- clients.responses(5, SubscribeActions*)
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(3))
        responses <- clients.responses(15)
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
            responses.idRecords(1, userId = 1).size == 3 &&
            responses.idRecords(1, userId = 2).size == 3 &&
            responses.idRecords(2, userId = 2).size == 3 &&
            responses.idRecords(1, userId = 3).size == 3 &&
            responses.idRecords(3, userId = 3).size == 3
    },
    test("Unsubscribe by Id") {
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.responses(5, SubscribeActions*)
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.responses(1, (2, SyncRequest(unsubscribeIds = Seq(1))))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(3))
        responses <- clients.responses(14)
      yield assertTrue:
        responses.length == 15 &&
          responses.idRecords(1, userId = 1).size == 3 &&
          responses.idRecords(1, userId = 2).size == 1 &&
          responses.idRecords(2, userId = 2).size == 3 &&
          responses.idRecords(1, userId = 3).size == 3 &&
          responses.idRecords(3, userId = 3).size == 3
    },
    test("Unsubscribe All") {
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.responses(5, SubscribeActions*)
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.responses(2, (2, SyncRequest(unsubscribeAll = true)))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(3))
        responses <- clients.responses(11)
      yield assertTrue:
        responses.length == 11 &&
          responses.idRecords(1, userId = 1).size == 3 &&
          responses.idRecords(1, userId = 2).size == 1 &&
          responses.idRecords(2, userId = 2).size == 1 &&
          responses.idRecords(1, userId = 3).size == 3 &&
          responses.idRecords(3, userId = 3).size == 3
    },
    test("Subscribe Response when previous_etag matches") {
      val initialData = ZSyncServiceImplSpec.createUpdateRequest(1)
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        subcribe1Response <- clients.responses(1, (1, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1)))))
        subscribe2Response <- clients.responses(2, (2, SyncRequest(
          subscribes = Seq(
          SyncRequest.Subscribe.of(id = 1, previousEtag = initialData.updates.etag(1)),
          SyncRequest.Subscribe(id = 2),
        ))))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        responses <- clients.responses(3)
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
      t => t._1 == userId && t._2.data.exists(_.id == id)
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

  def createUpdateRequest(batch: Int): UpdateRequest = {
    val dataUpdates = createData(batch).zip(createData(batch - 1)).map {
      (data, previousData) => DataUpdate.of(Some(data), DataRecord.calculateEtag(previousData))
    }
    UpdateRequest.of(updates = dataUpdates)
  }
}

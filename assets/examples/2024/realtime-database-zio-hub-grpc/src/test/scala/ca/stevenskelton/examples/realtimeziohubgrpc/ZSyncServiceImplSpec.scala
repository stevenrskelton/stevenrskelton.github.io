package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.ETag
import ca.stevenskelton.examples.realtimeziohubgrpc.ZSyncServiceImplSpec.{etag, idRecords, stream}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.DataUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse}
import zio.stream.{ZStream, UStream}
import zio.test.assertTrue
import zio.test.junit.JUnitRunnableSpec

class ZSyncServiceImplSpec extends JUnitRunnableSpec {

  // Client 1:
  //  - watch 1
  //  - update 1,2,3,4 (Time 1000)
  //  - update 1,2,3,4 (Time 2000)
  //  - update 1,2,3,4 (Time 3000)
  // Client 2:
  //  - watch 1,2
  // Client 3:
  //  - watch 1,3
  val SubscribeActions: Seq[(UserId, SyncRequest)] = Seq(
    (1, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1)))),
    (2, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1), SyncRequest.Subscribe(id = 2)))),
    (3, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = 1), SyncRequest.Subscribe(id = 3)))),
  )

  override def spec = suite("multiple client listeners")(
    test("All updated") {
      val ExpectedResponseCount = 15
      val streamActions = SubscribeActions ++ Seq(
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(1))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(3))),
      )
      for
        userResponses <- BidirectionalTestClients(streamActions.stream, ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)

        //Client 1
        val client1Responses = userResponses.withFilter(_._1 == 1).map(_._2)
        client1Responses.foreach(o => println(o.toString))
        assertTrue(userResponses.idRecords(1, userId = 1).size == 3)

        //Client 2
        val client2Responses = userResponses.withFilter(_._1 == 2).map(_._2)
        client2Responses.foreach(o => println(o.toString))
        assertTrue(userResponses.idRecords(1, userId = 2).size == 3)
        assertTrue(userResponses.idRecords(2, userId = 2).size == 3)

        //Client 3
        val client3Responses = userResponses.withFilter(_._1 == 3).map(_._2)
        client3Responses.foreach(o => println(o.toString))
        assertTrue(userResponses.idRecords(1, userId = 3).size == 3)
        assertTrue(userResponses.idRecords(3, userId = 3).size == 3)
    },
    test("Unsubscribe by Id") {
      val ExpectedResponseCount = 12
      val streamActions = SubscribeActions ++ Seq(
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(1))),
        (2, SyncRequest(unsubscribeIds = Seq(1))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(3))),
      )
      for
        userResponses <- BidirectionalTestClients(streamActions.stream, ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)
        assertTrue(userResponses.idRecords(1, userId = 1).size == 3)
//        assertTrue(userResponses.idRecords(1, userId = 2).size == 1)
        assertTrue(userResponses.idRecords(2, userId = 2).size == 3)
        assertTrue(userResponses.idRecords(1, userId = 3).size == 3)
        assertTrue(userResponses.idRecords(3, userId = 3).size == 3)
    },
    test("Unsubscribe All") {
      val ExpectedResponseCount = 9
      val streamActions = SubscribeActions ++ Seq(
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(1))),
        (2, SyncRequest(unsubscribeAll = true)),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(2))),
        (1, SyncRequest(updates = ZSyncServiceImplSpec.createDataUpdates(3))),
      )
      for
        userResponses <- BidirectionalTestClients(streamActions.stream, ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)
        assertTrue(userResponses.idRecords(1, userId = 1).size == 3)
//        assertTrue(userResponses.idRecords(1, userId = 2).size == 1)
//        assertTrue(userResponses.idRecords(2, userId = 2).size == 1)
        assertTrue(userResponses.idRecords(1, userId = 3).size == 3)
        assertTrue(userResponses.idRecords(3, userId = 3).size == 3)
    },
    test("Limit Subscribe Response when previous_etag matches") {
      val ExpectedResponseCount = 2
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
        userResponses <- BidirectionalTestClients(streamActions.stream, ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)
        assertTrue(userResponses.idRecords(1).size == 1)
        assertTrue(userResponses.idRecords(2).size == 2)
    }
  ) @@ zio.test.TestAspect.sequential
}

object ZSyncServiceImplSpec {

  extension (userResponses: Seq[(UserId, SyncResponse)])
    def idRecords(id: Int, userId: UserId = 1): Seq[Data] = userResponses.filter {
      t => t._1 == userId && t._2.data.get.id == id
    }.flatMap(_._2.data)

  extension (dataUpdates: Seq[DataUpdate])
    def etag(id: Int): ETag = ZSyncServiceImpl.calculateEtag(dataUpdates.find(_.data.exists(_.id == id)).get.data.get)

  extension (streamActions: Seq[(UserId, SyncRequest)])
    def stream: UStream[(UserId, SyncRequest)] = ZStream.fromIterable(streamActions, 1)//.throttleShape(1, 1.milliseconds)(_.size)

  private def createData(batch: Int): Seq[Data] = Seq(
    Data.of(id = 1, field1 = s"batch$batch"),
    Data.of(id = 2, field1 = s"batch$batch"),
    Data.of(id = 3, field1 = s"batch$batch"),
    Data.of(id = 4, field1 = s"batch$batch"),
    Data.of(id = 5, field1 = s"batch$batch"),
  )

  def createDataUpdates(batch: Int): Seq[DataUpdate] = createData(batch).zip(createData(batch - 1)).map:
    (data, previousData) => DataUpdate.of(Some(data), ZSyncServiceImpl.calculateEtag(previousData))

}

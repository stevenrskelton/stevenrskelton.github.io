package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.ETag
import ca.stevenskelton.examples.realtimeziohubgrpc.ZSyncServiceImplSpec.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateRequest.DataUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, UpdateRequest}
import zio.ZIO
import zio.stream.{UStream, ZStream}
import zio.test.assertTrue
import zio.test.junit.JUnitRunnableSpec

class ZSyncServiceImplSpec extends JUnitRunnableSpec {

  override def spec = suite("multiple client listeners")(
    test("All updated") {
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.responses(5, SubscribeActions *)
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id1))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id2))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id3))
        responses <- clients.responses(15)
      yield

        //Client 1
        val client1Responses = responses.withFilter(_._1 == User1).map(_._2)
        client1Responses.foreach(o => println(o.toString))

        //Client 2
        val client2Responses = responses.withFilter(_._1 == User2).map(_._2)
        client2Responses.foreach(o => println(o.toString))

        //Client 3
        val client3Responses = responses.withFilter(_._1 == User3).map(_._2)
        client3Responses.foreach(o => println(o.toString))

        assertTrue:

          responses.size == 15 &&
            responses.idRecords(Id1, User1).size == 3 &&
            responses.idRecords(Id1, User2).size == 3 &&
            responses.idRecords(Id2, User2).size == 3 &&
            responses.idRecords(Id1, User3).size == 3 &&
            responses.idRecords(Id3, User3).size == 3
    },
    test("Unsubscribe by Id") {
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.responses(5, SubscribeActions *)
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id1))
        _ <- clients.responses(6, (User2, SyncRequest(unsubscribeIds = Seq(Id1))))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id2))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id3))
        responses <- clients.responses(8)
      yield assertTrue:
        responses.length == 8 &&
          responses.idRecords(Id1, User1).size == 2 &&
          responses.idRecords(Id1, User2).isEmpty &&
          responses.idRecords(Id2, User2).size == 2 &&
          responses.idRecords(Id1, User3).size == 2 &&
          responses.idRecords(Id3, User3).size == 2
    },
    test("Unsubscribe All") {
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.responses(5, SubscribeActions *)
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id1))
        _ <- clients.responses(7, (User2, SyncRequest(unsubscribeAll = true)))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id2))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(Id3))
        responses <- clients.responses(6)
      yield assertTrue:
        responses.length == 6 &&
          responses.idRecords(Id1, User1).size == 2 &&
          responses.idRecords(Id1, User2).isEmpty &&
          responses.idRecords(Id2, User2).isEmpty &&
          responses.idRecords(Id1, User3).size == 2 &&
          responses.idRecords(Id3, User3).size == 2
    },
    test("Subscribe Response when previous_etag matches") {
      val initialData = ZSyncServiceImplSpec.createUpdateRequest(1)
      for
        clients <- BidirectionalTestClients.launch
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.responses(1, (User1, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = Id1)))))
        subscribe2Response <- clients.responses(2, (User2, SyncRequest(
          subscribes = Seq(
            SyncRequest.Subscribe.of(id = Id1, previousEtag = initialData.updates.etag(Id1)),
            SyncRequest.Subscribe(id = Id2),
          ))))
        _ <- clients.client1.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        responses <- clients.responses(3)
      yield assertTrue:
          subscribe2Response.find(_._2.id == Id1).get._2.data.isEmpty &&
          subscribe2Response.find(_._2.id == Id2).get._2.data.isDefined &&
          responses.length == 3 &&
          subscribe2Response.size == 2 &&
          responses.idRecords(Id1, User1).size == 1 &&
          responses.idRecords(Id1, User2).size == 1 &&
          responses.idRecords(Id2, User2).size == 1
    },
    test("Iterate active subscriptions") {
      for
        clients <- BidirectionalTestClients.launch
        activeSubscribers0 <- clients.zSyncServiceImpl.activeSubscribersRef.get
        responses <- clients.responses(5, SubscribeActions *)
        activeSubscribers1 <- clients.zSyncServiceImpl.activeSubscribersRef.get
        subscriptionCount <- ZIO.collectAll {
          activeSubscribers1.map {
            _.get.map(manager => (manager.authenticatedUser.userId, manager.getSubscriptions.toSeq))
          }
        }
      yield assertTrue:
        activeSubscribers0.isEmpty &&
          responses.size == 5 &&
          activeSubscribers1.size == 3 &&
          subscriptionCount.flatMap(_._2).size == 5
    },
  ) @@ zio.test.TestAspect.sequential //@@ zio.test.TestAspect.timeout(5.seconds)
}

object ZSyncServiceImplSpec {

  private val User1: UserId = 1
  private val User2: UserId = 2
  private val User3: UserId = 3

  private val Id1 = 1
  private val Id2 = 2
  private val Id3 = 3
  private val Id4 = 4
  private val Id5 = 5

  val SubscribeActions: Seq[(UserId, SyncRequest)] = Seq(
    //watch 1
    (User1, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = Id1)))),
    //watch 1,2
    (User2, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = Id1), SyncRequest.Subscribe(id = Id2)))),
    //watch 1,3
    (User3, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = Id1), SyncRequest.Subscribe(id = Id3)))),
  )

  extension (userResponses: Seq[(UserId, SyncResponse)])
    def idRecords(id: Int, userId: UserId): Seq[Data] = userResponses.filter {
      t => t._1 == userId && t._2.data.exists(_.id == id)
    }.flatMap(_._2.data)

  extension (dataUpdates: Seq[DataUpdate])
    def etag(id: Int): ETag = DataRecord.calculateEtag(dataUpdates.find(_.data.exists(_.id == id)).get.data.get)

  extension (streamActions: Seq[(UserId, SyncRequest)])
    def stream: UStream[(UserId, SyncRequest)] = ZStream.fromIterable(streamActions, 1) //.throttleShape(1, 1.milliseconds)(_.size)

  private def createData(batch: Int): Seq[Data] = Seq(
    Data.of(id = Id1, field1 = s"batch-$batch"),
    Data.of(id = Id2, field1 = s"batch-$batch"),
    Data.of(id = Id3, field1 = s"batch-$batch"),
    Data.of(id = Id4, field1 = s"batch-$batch"),
    Data.of(id = Id5, field1 = s"batch-$batch"),
  )

  def createUpdateRequest(batch: Int): UpdateRequest = {
    val dataUpdates = createData(batch).zip(createData(batch - 1)).map {
      (data, previousData) => DataUpdate.of(Some(data), DataRecord.calculateEtag(previousData))
    }
    UpdateRequest.of(updates = dataUpdates)
  }
}

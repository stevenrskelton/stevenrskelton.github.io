package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.ETag
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncResponse.State.LOADING
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateRequest.DataUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, UpdateRequest, UpdateResponse}
import ca.stevenskelton.examples.realtimeziohubgrpc.{BidirectionalTestClients, DataRecord}
import zio.stream.{UStream, ZStream}
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Live, Spec, TestEnvironment, assertTrue}
import zio.{Scope, ZIO, ZLayer, durationInt}

class ZSyncServiceImplSpec extends JUnitRunnableSpec:
  override def spec: Spec[TestEnvironment & Scope, Any] = ZSyncServiceImplSpec.spec

object ZSyncServiceImplSpec extends JUnitRunnableSpec:

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
    def idRecords(id: Int, userId: UserId): Seq[Data] = userResponses
      .withFilter((uId, dataRecord) => uId == userId && dataRecord.data.exists(_.id == id))
      .flatMap(_._2.data)

    def loading(id: Int, userId: UserId): Seq[SyncResponse] = userResponses
      .withFilter((uId, dataRecord) => uId == userId && dataRecord.id == id && dataRecord.state == SyncResponse.State.LOADING)
      .map(_._2)

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

  def createUpdateRequest(batch: Int): UpdateRequest = UpdateRequest.of(
    updates = createData(batch)
      .zip(createData(batch - 1))
      .map((data, previousData) => DataUpdate.of(Some(data), DataRecord.calculateEtag(previousData)))
  )

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("multiple client listeners")(
    test("All updated") {
      for
        zSyncService <- ZSyncServiceImpl.launch.provideLayer(ExternalDataLayer.live)
        clients <- BidirectionalTestClients.launch(zSyncService)
        _ <- clients.responses(5, SubscribeActions *)
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(3))
        responses <- clients.responses(15)
      yield assertTrue:
        responses.size == 15 &&
          responses.idRecords(Id1, User1).size == 3 &&
          responses.idRecords(Id1, User2).size == 3 &&
          responses.idRecords(Id2, User2).size == 3 &&
          responses.idRecords(Id1, User3).size == 3 &&
          responses.idRecords(Id3, User3).size == 3
    },
    test("Unsubscribe by Id") {
      for
        zSyncService <- ZSyncServiceImpl.launch.provideLayer(ExternalDataLayer.live)
        clients <- BidirectionalTestClients.launch(zSyncService)
        _ <- clients.responses(5, SubscribeActions *)
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.responses(6, (User2, SyncRequest(unsubscribes = Seq(SyncRequest.Unsubscribe(Id1)))))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(3))
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
        zSyncService <- ZSyncServiceImpl.launch.provideLayer(ExternalDataLayer.live)
        clients <- BidirectionalTestClients.launch(zSyncService)
        _ <- clients.responses(5, SubscribeActions *)
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.responses(7, (User2, SyncRequest(unsubscribes = Seq(SyncRequest.Unsubscribe(all = true)))))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(2))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(3))
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
        zSyncService <- ZSyncServiceImpl.launch.provideLayer(ExternalDataLayer.live)
        clients <- BidirectionalTestClients.launch(zSyncService)
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        _ <- clients.responses(1, (User1, SyncRequest(subscribes = Seq(SyncRequest.Subscribe(id = Id1)))))
        subscribe2Response <- clients.responses(2, (User2, SyncRequest(
          subscribes = Seq(
            SyncRequest.Subscribe.of(id = Id1, previousEtag = initialData.updates.etag(Id1)),
            SyncRequest.Subscribe(id = Id2),
          ))))
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(2))
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
        zSyncService <- ZSyncServiceImpl.launch.provideLayer(ExternalDataLayer.live)
        clients <- BidirectionalTestClients.launch(zSyncService)
        subscribedIds0 <- zSyncService.externalDataService.subscribedIds
        responses <- clients.responses(5, SubscribeActions *)
        subscribedIds1 <- zSyncService.externalDataService.subscribedIds
        _ <- clients.client3.requests.shutdown
        _ <- Live.live(ZIO.attempt("pause for shutdown").delay(100.milliseconds))
        subscribedIds2 <- zSyncService.externalDataService.subscribedIds
      yield assertTrue:
        subscribedIds0.isEmpty &&
          responses.size == 5 &&
          subscribedIds1 == Set(1, 2, 3) &&
          subscribedIds2 == Set(1, 2)
    },
    test("Fetch loaded") {
      for
        zSyncService <- ZSyncServiceImpl.launch.provideLayer(ZLayer.succeed(new ExternalDataLayer(createData(0))))
        clients <- BidirectionalTestClients.launch(zSyncService)
        subscribeResponses <- clients.responses(10, SubscribeActions *)
        _ <- zSyncService.externalDataService.update(ZSyncServiceImplSpec.createUpdateRequest(1))
        responses <- clients.responses(5)
      yield assertTrue:
        subscribeResponses.size == 10 &&
          subscribeResponses.idRecords(Id1, User1) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id1, User2) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id2, User2) == Seq(Data.of(Id2, "batch-0")) &&
          subscribeResponses.idRecords(Id1, User3) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id3, User3) == Seq(Data.of(Id3, "batch-0")) &&
          subscribeResponses.loading(Id1, User1) == Seq(SyncResponse.of(Id1, "", None, LOADING)) &&
          subscribeResponses.loading(Id1, User2) == Seq(SyncResponse.of(Id1, "", None, LOADING)) &&
          subscribeResponses.loading(Id2, User2) == Seq(SyncResponse.of(Id2, "", None, LOADING)) &&
          subscribeResponses.loading(Id1, User3) == Seq(SyncResponse.of(Id1, "", None, LOADING)) &&
          subscribeResponses.loading(Id3, User3) == Seq(SyncResponse.of(Id3, "", None, LOADING)) &&
          responses.size == 5 &&
          responses.idRecords(Id1, User1) == Seq(Data.of(Id1, "batch-1")) &&
          responses.idRecords(Id1, User2) == Seq(Data.of(Id1, "batch-1")) &&
          responses.idRecords(Id2, User2) == Seq(Data.of(Id2, "batch-1")) &&
          responses.idRecords(Id1, User3) == Seq(Data.of(Id1, "batch-1")) &&
          responses.idRecords(Id3, User3) == Seq(Data.of(Id3, "batch-1"))
    },
  ) @@ zio.test.TestAspect.sequential //@@ zio.test.TestAspect.timeout(5.seconds)



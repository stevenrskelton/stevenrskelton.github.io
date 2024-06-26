package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncResponse.State.LOADING
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse}
import ca.stevenskelton.examples.realtimeziohubgrpc.{BidirectionalTestClients, DataRecord}
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Live, Spec, TestClock, TestEnvironment, assertTrue}
import zio.{Schedule, Scope, ZIO, ZLayer, durationInt}

class ZSyncServiceImplSpec extends JUnitRunnableSpec:
  override def spec: Spec[TestEnvironment & Scope, Any] = ZSyncServiceImplSpec.spec

object ZSyncServiceImplSpec extends JUnitRunnableSpec:

  private val User1: UserId = 1
  private val User2: UserId = 2
  private val User3: UserId = 3

  private val Id1: DataId = 1
  private val Id2: DataId = 2
  private val Id3: DataId = 3
  private val Id4: DataId = 4
  private val Id5: DataId = 5

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

    def isLoading(id: DataId, userId: UserId): Boolean = userResponses
      .exists((uId, dataRecord) => uId == userId && dataRecord.id == id && dataRecord.state == SyncResponse.State.LOADING)

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("multiple client listeners")(
    test("Iterate active subscriptions") {
      for
        zSyncService <- ZSyncServiceImpl.launch().provideLayer(HardcodedExternalDataLayer.live(Nil, Schedule.stop))
        clients <- BidirectionalTestClients.launch(zSyncService)
        subscribedIds0 <- ExternalDataLayer.subscribedIds(zSyncService.globalSubscribersRef)
        responses <- clients.responses(5, SubscribeActions *)
        subscribedIds1 <- ExternalDataLayer.subscribedIds(zSyncService.globalSubscribersRef)
        _ <- clients.client3.requests.shutdown
        _ <- Live.live(ZIO.attempt("pause for shutdown").delay(1000.milliseconds))
        subscribedIds2 <- ExternalDataLayer.subscribedIds(zSyncService.globalSubscribersRef)
      yield assertTrue:
        subscribedIds0.isEmpty &&
          responses.size == 5 &&
          subscribedIds1 == Set(1, 2, 3) &&
          subscribedIds2 == Set(1, 2)
    },
    test("fetch loading") {
      val externalData = Seq(
        Data.of(id = Id1, field1 = "batch-0"),
        Data.of(id = Id1, field1 = "batch-0"),
        Data.of(id = Id1, field1 = "batch-0"),
        Data.of(id = Id2, field1 = "batch-0"),
        Data.of(id = Id3, field1 = "batch-0"),
      )
      for
        zSyncService <- ZSyncServiceImpl.launch().provideLayer(HardcodedExternalDataLayer.live(externalData, Schedule.stop))
        clients <- BidirectionalTestClients.launch(zSyncService)
        subscribeResponses <- clients.responses(10, SubscribeActions *)
      yield assertTrue:
        subscribeResponses.size == 10 &&
          subscribeResponses.isLoading(Id1, User1) &&
          subscribeResponses.isLoading(Id1, User2) &&
          subscribeResponses.isLoading(Id2, User2) &&
          subscribeResponses.isLoading(Id1, User3) &&
          subscribeResponses.isLoading(Id3, User3) &&
          subscribeResponses.idRecords(Id1, User1) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id1, User2) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id2, User2) == Seq(Data.of(Id2, "batch-0")) &&
          subscribeResponses.idRecords(Id1, User3) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id3, User3) == Seq(Data.of(Id3, "batch-0"))
    },
    test("Scheduled fetch") {
      val externalData = Seq(
        Data.of(id = Id1, field1 = "batch-1"),
        Data.of(id = Id2, field1 = "batch-1"),
        Data.of(id = Id3, field1 = "batch-1"),
        Data.of(id = Id1, field1 = "batch-2"),
        Data.of(id = Id2, field1 = "batch-2"),
        Data.of(id = Id3, field1 = "batch-2"),
      )
      val refreshSchedule = Schedule.fixed(1.second)
      val initialData = Seq(
        Data.of(id = Id1, field1 = "batch-0"),
        Data.of(id = Id2, field1 = "batch-0"),
        Data.of(id = Id3, field1 = "batch-0"),
      )
      for
        zSyncService <- ZSyncServiceImpl.launch(initialData).provideLayer(HardcodedExternalDataLayer.live(externalData, refreshSchedule))
        clients <- BidirectionalTestClients.launch(zSyncService)
        subscribeResponses <- clients.responses(5, SubscribeActions *)
        _ <- TestClock.adjust(1.second)
        scheduledResponses1 <- clients.responses(5)
        _ <- TestClock.adjust(1.second)
        scheduledResponses2 <- clients.responses(5)
      yield assertTrue:
        subscribeResponses.size == 5 &&
          subscribeResponses.idRecords(Id1, User1) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id1, User2) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id2, User2) == Seq(Data.of(Id2, "batch-0")) &&
          subscribeResponses.idRecords(Id1, User3) == Seq(Data.of(Id1, "batch-0")) &&
          subscribeResponses.idRecords(Id3, User3) == Seq(Data.of(Id3, "batch-0")) &&
          scheduledResponses1.size == 5 &&
          scheduledResponses1.idRecords(Id1, User1) == Seq(Data.of(Id1, "batch-1")) &&
          scheduledResponses1.idRecords(Id1, User2) == Seq(Data.of(Id1, "batch-1")) &&
          scheduledResponses1.idRecords(Id2, User2) == Seq(Data.of(Id2, "batch-1")) &&
          scheduledResponses1.idRecords(Id1, User3) == Seq(Data.of(Id1, "batch-1")) &&
          scheduledResponses1.idRecords(Id3, User3) == Seq(Data.of(Id3, "batch-1")) &&
          scheduledResponses2.size == 5 &&
          scheduledResponses2.idRecords(Id1, User1) == Seq(Data.of(Id1, "batch-2")) &&
          scheduledResponses2.idRecords(Id1, User2) == Seq(Data.of(Id1, "batch-2")) &&
          scheduledResponses2.idRecords(Id2, User2) == Seq(Data.of(Id2, "batch-2")) &&
          scheduledResponses2.idRecords(Id1, User3) == Seq(Data.of(Id1, "batch-2")) &&
          scheduledResponses2.idRecords(Id3, User3) == Seq(Data.of(Id3, "batch-2"))
    },
  ) @@ zio.test.TestAspect.timeout(5.seconds) @@ zio.test.TestAspect.sequential



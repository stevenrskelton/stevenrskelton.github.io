package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import ca.stevenskelton.examples.realtimeziohubgrpc.{AuthenticatedUser, Commands, DataRecord}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Clock, Hub, IO, Ref, URIO, ZIO}

import scala.collection.immutable.HashSet
import scala.collection.mutable

object ZSyncServiceImpl:

  private val HubCapacity = 1000

  def launch(initial: Seq[Data] = Nil): URIO[ExternalDataLayer, ZSyncServiceImpl] =
    ZIO.serviceWithZIO[ExternalDataLayer]:
      externalDataLayer =>
        for
          journal <- Hub.sliding[DataRecord](HubCapacity)
          now <- Clock.instant
          initialMap = initial.map(data => data.id -> DataRecord(data, now, DataRecord.calculateEtag(data))).toMap
          databaseRecordsRef <- Ref.make[Map[DataId, DataRecord]](initialMap)
          globalSubscribersRef <- Ref.make[Set[Ref[HashSet[DataId]]]](Set.empty)
          externalDataService <- externalDataLayer.createService(journal, databaseRecordsRef, globalSubscribersRef)
        yield
          ZSyncServiceImpl(journal, databaseRecordsRef, globalSubscribersRef, externalDataService)

case class ZSyncServiceImpl private(
                                     journal: Hub[DataRecord],
                                     databaseRecordsRef: Ref[Map[DataId, DataRecord]],
                                     globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]],
                                     externalDataService: ExternalDataService,
                                   ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        userSubscriptionsRef <- Ref.make(HashSet.empty[DataId])
        updateStream <- Commands.userSubscriptionStream(userSubscriptionsRef, journal)
        _ <- globalSubscribersRef.update(_ + userSubscriptionsRef)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.fromIterableZIO:
              databaseRecordsRef.get.flatMap:
                Commands.modifyUserSubscriptions(syncRequest, userSubscriptionsRef, _)
              .flatMap:
                syncResponses =>
                  val idsToFetch = new mutable.HashSet[DataId]()
                  val loadingResponses = syncResponses.map:
                    syncResponse =>
                      if syncResponse.state == SyncResponse.State.NOT_FOUND then
                        idsToFetch.add(syncResponse.id)
                        syncResponse.copy(state = SyncResponse.State.LOADING)
                      else syncResponse

                  if idsToFetch.nonEmpty then externalDataService.queueFetchAll(idsToFetch).as(loadingResponses)
                  else ZIO.succeed(loadingResponses)

        val endOfAllRequestsStream = ZStream.finalizer:
          globalSubscribersRef.update:
            _.filter(_ != userSubscriptionsRef)
          *> ZIO.log(s"Finalizing user-${context.userId}")
        .drain

        updateStream.merge(requestStreams ++ endOfAllRequestsStream, strategy = HaltStrategy.Right)

  override def update(request: UpdateRequest, context: AuthenticatedUser): IO[StatusException, UpdateResponse] = ???
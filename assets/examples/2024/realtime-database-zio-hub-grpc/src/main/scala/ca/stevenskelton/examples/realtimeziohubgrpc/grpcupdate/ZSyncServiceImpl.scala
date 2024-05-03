package ca.stevenskelton.examples.realtimeziohubgrpc.grpcupdate

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import ca.stevenskelton.examples.realtimeziohubgrpc.{AuthenticatedUser, Commands, DataRecord}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, UIO}

import scala.collection.immutable.HashSet

object ZSyncServiceImpl:

  private val HubCapacity = 1000

  def launch: UIO[ZSyncServiceImpl] =
    for
      journal <- Hub.sliding[DataRecord](HubCapacity)
      databaseRecordsRef <- Ref.make[Map[DataId, DataRecord]](Map.empty)
    yield
      ZSyncServiceImpl(journal, databaseRecordsRef)


case class ZSyncServiceImpl private(
                                     journal: Hub[DataRecord],
                                     databaseRecordsRef: Ref[Map[DataId, DataRecord]]
                                   ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        userSubscriptionsRef <- Ref.make(HashSet.empty[DataId])
        updateStream <- Commands.userSubscriptionStream(userSubscriptionsRef, journal)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.fromIterableZIO:
              databaseRecordsRef.get.flatMap:
                Commands.modifyUserSubscriptions(syncRequest, userSubscriptionsRef, _)

        updateStream.merge(requestStreams, strategy = HaltStrategy.Right)

  override def update(request: UpdateRequest, context: AuthenticatedUser): IO[StatusException, UpdateResponse] =
    Commands.updateDatabaseRecords(request, journal, databaseRecordsRef)
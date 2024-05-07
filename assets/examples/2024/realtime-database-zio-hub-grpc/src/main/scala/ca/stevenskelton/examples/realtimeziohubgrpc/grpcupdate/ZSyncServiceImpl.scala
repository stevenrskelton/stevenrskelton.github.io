package ca.stevenskelton.examples.realtimeziohubgrpc.grpcupdate

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.ZioSyncService.SyncService
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import ca.stevenskelton.examples.realtimeziohubgrpc.{DataRecord, Effects}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, UIO}

import scala.collection.immutable.HashSet

object ZSyncServiceImpl:
  
  def launch(hubCapacity: Int = 1000): UIO[ZSyncServiceImpl] =
    for
      journal <- Hub.sliding[DataRecord](hubCapacity)
      databaseRecordsRef <- Ref.make[Map[DataId, DataRecord]](Map.empty)
    yield
      ZSyncServiceImpl(journal, databaseRecordsRef)


case class ZSyncServiceImpl private(
                                     journal: Hub[DataRecord],
                                     databaseRecordsRef: Ref[Map[DataId, DataRecord]]
                                   ) extends SyncService:
  /**
   * Requests will subscribe/unsubscribe to `Data`.
   * Data updates of subscribed elements is streamed in realtime.
   */
  override def bidirectionalStream(request: Stream[StatusException, SyncRequest]): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        userSubscriptionsRef <- Ref.make(HashSet.empty[DataId])
        updateStream <- Effects.userSubscriptionStream(userSubscriptionsRef, journal)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.fromIterableZIO:
              databaseRecordsRef.get.flatMap:
                Effects.modifyUserSubscriptions(syncRequest, userSubscriptionsRef, _)

        updateStream.merge(requestStreams, strategy = HaltStrategy.Right)

  /**
   * Creation / Update of `Data`. Response will indicate success or failure due to write conflict.
   * Conflicts are detected based on the ETag in the request.
   */
  override def update(request: UpdateRequest): IO[StatusException, UpdateResponse] =
    Effects.updateDatabaseRecords(request, journal, databaseRecordsRef)
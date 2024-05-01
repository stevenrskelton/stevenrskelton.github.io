package ca.stevenskelton.examples.realtimeziohubgrpc.grpcupdate

import ca.stevenskelton.examples.realtimeziohubgrpc.commands.{DatabaseUpdate, ModifyUserSubscriptions}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import ca.stevenskelton.examples.realtimeziohubgrpc.{AuthenticatedUser, DataRecord}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, Scope, UIO, ZIO}

import scala.collection.immutable.HashSet

object ZSyncServiceImpl:

  private val HubCapacity = 1000
  private val HubMaxChunkSize = 1000

  def launch: UIO[ZSyncServiceImpl] =
    for
      journal <- Hub.sliding[DataRecord](HubCapacity)
      databaseRecordsRef <- Ref.make[Map[Int, DataRecord]](Map.empty)
    yield
      ZSyncServiceImpl(journal, databaseRecordsRef)


case class ZSyncServiceImpl private(
                                     journal: Hub[DataRecord],
                                     databaseRecordsRef: Ref[Map[Int, DataRecord]]
                                   ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        userSubscriptionsRef <- Ref.make(HashSet.empty[Int])
        updateStream <- createUserSubscriptionStream(userSubscriptionsRef)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.fromIterableZIO:
              databaseRecordsRef.get.flatMap:
                databaseRecords => ModifyUserSubscriptions.process(syncRequest, userSubscriptionsRef, databaseRecords)

        updateStream.merge(requestStreams, strategy = HaltStrategy.Right)


  private def createUserSubscriptionStream(userSubscriptionsRef: Ref[HashSet[Int]]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(journal, ZSyncServiceImpl.HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord =>
          userSubscriptionsRef.get.map(_.contains(dataRecord.data.id))
      .map:
        dataRecord => SyncResponse.of(dataRecord.data.id, dataRecord.etag, Some(dataRecord.data), SyncResponse.State.UPDATED)

  override def update(request: UpdateRequest, context: AuthenticatedUser): IO[StatusException, UpdateResponse] =
    DatabaseUpdate.process(request, journal, databaseRecordsRef)
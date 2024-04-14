package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.Subscribe
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, UStream, ZStream}
import zio.{Hub, IO, Ref, Scope, UIO, ZIO}

import scala.collection.mutable

object ZSyncServiceImpl:

  private val HubCapacity = 1000
  private val HubMaxChunkSize = 1000

  def launch: UIO[ZSyncServiceImpl] =
    for
      hub <- Hub.sliding[DataRecord](HubCapacity)
      database <- Ref.make[mutable.Map[Int, DataRecord]](mutable.Map.empty)
    yield
      ZSyncServiceImpl(hub, database)


case class ZSyncServiceImpl(
                             hub: Hub[DataRecord],
                             databaseRef: Ref[mutable.Map[Int, DataRecord]],
                           ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        dataStreamFilterRef <- initializeUser(context)
        hubStream <- createHubStream(dataStreamFilterRef)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.unwrap:
              ZIO.log(s"User${context.userId} Request: $syncRequest") *>
                ZIO.succeed:

                  val subscribeStream =
                    if (syncRequest.subscribes.nonEmpty) handleSubscribe(syncRequest.subscribes, dataStreamFilterRef)
                    else ZStream.empty

                  val unsubscribeStream =
                    if (syncRequest.unsubscribeAll) handleUnsubscribe(Nil, dataStreamFilterRef)
                    else if (syncRequest.unsubscribeIds.nonEmpty) handleUnsubscribe(syncRequest.unsubscribeIds, dataStreamFilterRef)
                    else ZStream.empty

                  (subscribeStream ++ unsubscribeStream).onError:
                    ex => ZIO.log(s"Exception on request $syncRequest for user-${context.userId}: ${ex.prettyPrint}")

        val endOfAllRequestsStream = ZStream
          .finalizer:
            ZIO.log(s"Finalizing user-${context.userId}")
          .drain

        hubStream.merge(requestStreams ++ endOfAllRequestsStream, strategy = HaltStrategy.Right)

  end bidirectionalStream


  private def initializeUser(context: AuthenticatedUser): IO[StatusException, Ref[DataStreamFilter]] =
    Ref.make(DataStreamFilter())


  private def createHubStream(dataStreamFilterRef: Ref[DataStreamFilter]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(hub, ZSyncServiceImpl.HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord =>
          dataStreamFilterRef.get.map:
            _.isWatching(dataRecord.data)
      .map:
        dataRecord => SyncResponse.of(dataRecord.data.id, dataRecord.etag, Some(dataRecord.data), SyncResponse.State.UPDATED)


  private def handleSubscribe(subscribes: Seq[Subscribe], dataStreamFilterRef: Ref[DataStreamFilter]): UStream[SyncResponse] =
    ZStream.fromIterableZIO:
      for
        dataMap <- databaseRef.get
        syncResponses <- dataStreamFilterRef.get.map:
          dataStreamFilter =>
            subscribes.map:
              subscribe =>
                val _ = dataStreamFilter.subscribe(subscribe.id)
                import SyncResponse.State.{LOADING, UNCHANGED, UPDATED}
                dataMap.get(subscribe.id) match
                  case Some(existing) if existing.etag == subscribe.previousEtag =>
                    SyncResponse.of(subscribe.id, existing.etag, None, UNCHANGED)
                  case Some(existing) =>
                    SyncResponse.of(subscribe.id, existing.etag, Some(existing.data), UPDATED)
                  case None =>
                    SyncResponse.of(subscribe.id, "", None, LOADING)
      yield
        syncResponses
  end handleSubscribe


  private def handleUnsubscribe(unsubscribeIds: Seq[Int], dataStreamFilterRef: Ref[DataStreamFilter]): UStream[SyncResponse] =
    ZStream.fromIterableZIO:
      dataStreamFilterRef.modify:
        dataStreamFilter => (dataStreamFilter.removeSubscription(unsubscribeIds), dataStreamFilter)
      .map:
        _.map((id, removed) => SyncResponse.of(id, "", None, SyncResponse.State.UNSUBSCRIBED))


  override def update(request: UpdateRequest, context: AuthenticatedUser): IO[StatusException, UpdateResponse] =
    import UpdateResponse.State.{CONFLICT, UNCHANGED, UPDATED}
    for
      now <- zio.Clock.instant
      updatesFlaggedConflict <- databaseRef.modify:
        database =>
          val updateIsConflict = request.updates
            .flatMap:
              dataUpdate => dataUpdate.data.map((_, dataUpdate.previousEtag))
            .map:
              (data, previousEtag) =>
                val updateETag = DataRecord.calculateEtag(data)
                database.get(data.id) match
                  case Some(existing) if existing.etag == updateETag => (existing, UNCHANGED)
                  case Some(existing) if existing.etag != previousEtag => (existing, CONFLICT)
                  case _ =>
                    val dataRecord = DataRecord(data, now, updateETag)
                    val _ = database.update(data.id, dataRecord)
                    (dataRecord, UPDATED)

          (updateIsConflict, database)

      dataUpdateStatuses <- ZIO.collectAll:
        updatesFlaggedConflict.map:
          case (dataRecord, UNCHANGED) =>
            ZIO.succeed(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, UNCHANGED, None))
          case (dataRecord, CONFLICT) =>
            ZIO.succeed(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, CONFLICT, Some(dataRecord.data)))
          case (dataRecord, UPDATED) =>
            hub.publish(dataRecord).as(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, UPDATED, None))
    yield
      UpdateResponse.of(updateStatuses = dataUpdateStatuses)

  end update
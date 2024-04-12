package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.{DataUpdate, Subscribe}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, ZioSyncService}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, Scope, UIO, ZIO}

import scala.collection.mutable

object ZSyncServiceImpl:
  
  private val HubCapacity = 1000
  
  def calculateEtag(data: Data): DataRecord.ETag = data.id.toString + data.field1
  
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
                    else ZIO.unit

                  val updateStream = 
                    if (syncRequest.updates.nonEmpty) handleUpdate(syncRequest.updates) 
                    else ZStream.empty

                  (subscribeStream ++ ZStream.fromZIO(unsubscribeStream).drain ++ updateStream).onError:
                    ex => ZIO.log(s"Exception on request $syncRequest for user-${context.userId}: ${ex.prettyPrint}")

                .map:
                  _.tap:
                    syncResponse => ZIO.log(s"Stream user-${context.userId}: $syncResponse")

        val endOfAllRequestsStream = ZStream
          .finalizer:
            ZIO.log(s"Finalizing user-${context.userId}")
          .drain

        hubStream.merge(requestStreams ++ endOfAllRequestsStream, strategy = HaltStrategy.Right)

  end bidirectionalStream


  private def initializeUser(context: AuthenticatedUser): IO[StatusException, Ref[DataStreamFilter]] =
    Ref.make(DataStreamFilter())


  private def createHubStream(dataStreamFilterRef: Ref[DataStreamFilter]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(hub, SyncServer.HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord =>
          dataStreamFilterRef.get.map:
            _.isWatching(dataRecord.data)
      .map:
        dataRecord => SyncResponse.of(Some(dataRecord.data), dataRecord.etag)


  private def handleSubscribe(subscribes: Seq[Subscribe], dataStreamFilterRef: Ref[DataStreamFilter]): Stream[StatusException, SyncResponse] =
    ZStream.fromIterableZIO:
      for
        dataMap <- databaseRef.get
        recordsWithNewEtags <- dataStreamFilterRef.get.map:
          dataStreamFilter =>
            subscribes.flatMap:
              subscribe =>
                val _ = dataStreamFilter.subscribe(subscribe.id)
                dataMap.get(subscribe.id).filterNot(_.etag == subscribe.previousEtag)
      yield
        recordsWithNewEtags.map:
          dataRecord => SyncResponse.of(Some(dataRecord.data), dataRecord.etag)


  private def handleUnsubscribe(unsubscribeIds: Seq[Int], dataStreamFilterRef: Ref[DataStreamFilter]): UIO[Unit] =
    dataStreamFilterRef
      .update:
        dataStreamFilter =>
          val _ = dataStreamFilter.unsubscribeAll(unsubscribeIds)
          dataStreamFilter

  private def handleUpdate(updates: Seq[DataUpdate]): Stream[StatusException, SyncResponse] =
    ZStream.fromIterableZIO:
      for
        now <- zio.Clock.instant
        updatesFlaggedConflict <- databaseRef.modify:
          database =>
            val updateIsConflict = updates
              .flatMap:
                dataUpdate => dataUpdate.data.map((_, dataUpdate.previousEtag))
              .flatMap:
                (data, previousEtag) =>
                  val updateETag = ZSyncServiceImpl.calculateEtag(data)
                  database.get(data.id) match
                    case Some(existing) if existing.etag == updateETag => None
                    case Some(existing) if existing.etag != previousEtag => Some(existing, true)
                    case _ =>
                      val dataRecord = DataRecord(data, now, updateETag)
                      val _ = database.update(data.id, dataRecord)
                      Some(dataRecord, false)

            (updateIsConflict, database)

        dataRecordConflicts <- ZIO.collectAll:
          updatesFlaggedConflict.map:
            case (dataRecord, true) => ZIO.succeed(Some(dataRecord))
            case (dataRecord, false) => hub.publish(dataRecord).as(None)

      yield
        dataRecordConflicts.flatten.map:
          dataRecord => SyncResponse.of(Some(dataRecord.data), dataRecord.etag)
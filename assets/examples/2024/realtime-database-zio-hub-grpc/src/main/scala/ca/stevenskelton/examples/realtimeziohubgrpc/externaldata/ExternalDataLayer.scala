package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, UpdateRequest}
import ca.stevenskelton.examples.realtimeziohubgrpc.{Commands, DataRecord}
import zio.stream.ZStream
import zio.{Chunk, Dequeue, Enqueue, Hub, NonEmptyChunk, Queue, Ref, Schedule, UIO, ZIO}

import scala.collection.immutable.HashSet

object ExternalDataLayer:
  /**
   * The set of all active subscriptions.
   */
  def subscribedIds(globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]]): UIO[Set[DataId]] =
    for
      globalSubscribers <- globalSubscribersRef.get
      subscribedIdSets <- ZIO.collectAll(globalSubscribers.map(_.get))
    yield
      subscribedIdSets.flatten

/**
 * Data layer that will refresh all subscribed data an external datasource based on a schedule.
 * @param refreshSchedule
 */
trait ExternalDataLayer(refreshSchedule: Schedule[Any, Any, Any]):

  /**
   * Implementation details for resolving elements in queue to external `Data`.
   *
   * @param chunk Will contain the `DataRecord` for records existing in `databaseRecordsRef`
   * @return `Data` from external source when available.
   */
  protected def externalData(chunk: NonEmptyChunk[Either[DataId, DataRecord]]): UIO[Chunk[Data]]

  /**
   * All DataId from this queue are fetched from external datasource.
   * Updates are commited to `databaseRecordsRef` and emitted to `journal`.
   * Creation of queue will start the automatic data refresh using `refreshSchedule`.
   * Closing queue will stop the scheduled refresh.
   * Calling multiple times will execute schedules in parallel, 
   * useful for manual refresh using `Schedule.once`.
   */
  def createFetchQueue(
                        journal: Hub[DataRecord],
                        databaseRecordsRef: Ref[Map[DataId, DataRecord]],
                        globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]],
                      ): UIO[Enqueue[DataId]] =
    for
      fetchQueue <- Queue.unbounded[DataId]
      _ <- attachFetchQueueListener(fetchQueue, journal, databaseRecordsRef).fork
      _ <- attachRefreshScheduler(fetchQueue, globalSubscribersRef).fork
    yield
      fetchQueue

  /**
   * Whenever DataId are queued for refresh, call `externalData` to get data,
   * Update `databaseRecordsRef` when new data available without conflicts,
   * Emit data updates to `journal`.
   */
  protected def attachFetchQueueListener(queue: Dequeue[DataId], journal: Hub[DataRecord], databaseRecordsRef: Ref[Map[DataId, DataRecord]]): UIO[Unit] =
    ZStream.fromQueue(queue).chunks.map(chunk => NonEmptyChunk.fromChunk(chunk.distinct)).collectSome.mapZIO:
      nonEmptyChunk =>
        databaseRecordsRef.get.map:
          database =>
            nonEmptyChunk.map:
              dataId =>
                database.get(dataId) match
                  case Some(dataRecord) => Right(dataRecord)
                  case None => Left(dataId)
        .flatMap:
          chunkFromDatabase =>
            externalData(chunkFromDatabase).flatMap:
              case chunk if chunk.isEmpty => ZIO.unit
              case chunk =>
                val updateRequest = UpdateRequest.of:
                  chunk.map:
                    data =>
                      val previousETag = chunkFromDatabase.find(_.exists(_.data.id == data.id)).flatMap(_.map(_.etag).toOption).getOrElse("")
                      UpdateRequest.DataUpdate.of(Some(data), previousETag)
                Commands.updateDatabaseRecords(updateRequest, journal, databaseRecordsRef)
    .runDrain

  /**
   * On schedule, attempt to refresh all subscribed data.
   */
  protected def attachRefreshScheduler(queue: Enqueue[DataId], globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]]): UIO[Unit] =
    ZStream.fromSchedule(refreshSchedule).mapZIO:
      _ => ExternalDataLayer.subscribedIds(globalSubscribersRef).flatMap(queue.offerAll)
    .runDrain

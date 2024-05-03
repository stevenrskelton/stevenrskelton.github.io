package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.State.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, UpdateRequest}
import ca.stevenskelton.examples.realtimeziohubgrpc.{Commands, DataRecord}
import zio.stream.ZStream
import zio.{Dequeue, Enqueue, Hub, Queue, Ref, Schedule, UIO, ULayer, ZIO, ZLayer}

import scala.collection.immutable.HashSet

object ExternalDataLayer:
  val live: ULayer[ExternalDataLayer] = ZLayer.succeed(ExternalDataLayer(Nil, Schedule.stop))

  /**
   * The set of all active subscriptions.
   */
  def subscribedIds(globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]]): UIO[Set[DataId]] =
    for
      globalSubscribers <- globalSubscribersRef.get
      subscribedIdSets <- ZIO.collectAll(globalSubscribers.map(_.get))
    yield
      subscribedIdSets.flatten

class ExternalDataLayer(hardcodedData: Seq[Data], refreshSchedule: Schedule[Any, Any, Any]):

  def createService(
                     journal: Hub[DataRecord],
                     databaseRecordsRef: Ref[Map[Int, DataRecord]],
                     globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                   ): UIO[ExternalDataService] =
    for
      fetchQueue <- Queue.unbounded[Int]
      _ <- attachFetchQueueListener(fetchQueue, journal, databaseRecordsRef).fork
      _ <- attachRefreshScheduler(fetchQueue, globalSubscribersRef).fork
    yield
      ExternalDataService(fetchQueue, journal, databaseRecordsRef, globalSubscribersRef)

  protected def attachFetchQueueListener(queue: Dequeue[DataId], journal: Hub[DataRecord], databaseRecordsRef: Ref[Map[DataId, DataRecord]]): UIO[Unit] =
    ZStream.fromQueue(queue).chunks.mapAccumZIO(hardcodedData):
      (fetchData, chunk) =>
        val (remainingFetchData, chunkData) = chunk.distinct.foldLeft((fetchData, List.empty[Data])):
          case ((foldFetchData, foldData), id) =>
            foldFetchData.find(_.id == id).map:
              data => (foldFetchData.filterNot(_ eq data), foldData :+ data)
            .getOrElse:
              (foldFetchData, foldData)

        val processUpdateRequest = if chunkData.isEmpty then ZIO.unit else databaseRecordsRef.get.flatMap:
          database =>
            val updateRequest = UpdateRequest.of:
              chunkData.map:
                data =>
                  val previousETag = database.get(data.id).map(_.etag).getOrElse("")
                  UpdateRequest.DataUpdate.of(Some(data), previousETag)

            Commands.updateDatabaseRecords(updateRequest, journal, databaseRecordsRef)

        processUpdateRequest.as(remainingFetchData, ())
    .runDrain

  protected def attachRefreshScheduler(queue: Enqueue[DataId], globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]]): UIO[Unit] =
    ZStream.fromSchedule(refreshSchedule).mapZIO:
      _ => ExternalDataLayer.subscribedIds(globalSubscribersRef).flatMap(queue.offerAll)
    .runDrain

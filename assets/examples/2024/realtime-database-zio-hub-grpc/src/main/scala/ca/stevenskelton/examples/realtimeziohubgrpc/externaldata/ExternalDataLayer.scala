package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.commands.DatabaseUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.State.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, UpdateRequest}
import zio.stream.ZStream
import zio.{Dequeue, Enqueue, Hub, Queue, Ref, Schedule, UIO, ULayer, ZIO, ZLayer, durationInt}

import scala.collection.immutable.HashSet

object ExternalDataLayer:
  val live: ULayer[ExternalDataLayer] = ZLayer.succeed(ExternalDataLayer(Nil, Schedule.stop))

  def subscribedIds(globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]]): UIO[Set[Int]] =
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

  protected def attachFetchQueueListener(queue: Dequeue[Int], journal: Hub[DataRecord], databaseRecordsRef: Ref[Map[Int, DataRecord]]): UIO[Unit] =
    var fetchData: Seq[Data] = hardcodedData
    ZStream.fromQueue(queue).mapZIO:
      id =>
        fetchData.find(_.id == id).map:
          data =>
            fetchData = fetchData.filterNot(_ eq data)
            databaseRecordsRef.get.flatMap:
              database =>
                val previousETag = database.get(id).map(_.etag).getOrElse("")
                val updateRequest = UpdateRequest.of(Seq(UpdateRequest.DataUpdate.of(Some(data), previousETag)))
                DatabaseUpdate.process(updateRequest, journal, databaseRecordsRef)
        .getOrElse:
          ZIO.unit
    .runDrain

  protected def attachRefreshScheduler(queue: Enqueue[Int], globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]]): UIO[Unit] =
    ZStream.fromSchedule(refreshSchedule).mapZIO:
      _ => ExternalDataLayer.subscribedIds(globalSubscribersRef).flatMap(queue.offerAll)
    .runDrain

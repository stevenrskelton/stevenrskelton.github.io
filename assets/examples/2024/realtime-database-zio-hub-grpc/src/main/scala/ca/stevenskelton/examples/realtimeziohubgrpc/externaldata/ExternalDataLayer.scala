package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, UpdateRequest}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateRequest.DataUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.State.*
import zio.stream.ZStream
import zio.{Chunk, Dequeue, Hub, Queue, Ref, UIO, ULayer, ZIO, ZLayer}

import scala.collection.immutable.HashSet

object ExternalDataLayer:
  val live: ULayer[ExternalDataLayer] = ZLayer.succeed(ExternalDataLayer(Nil))

class ExternalDataLayer(hardcodedData: Seq[Data]):

  def createService(
                     journal: Hub[DataRecord],
                     databaseRecordsRef: Ref[Map[Int, DataRecord]],
                     globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
            ): UIO[ExternalDataService] =
    for
      fetchQueue <- Queue.unbounded[Int]
    yield
      new ExternalDataService(fetchQueue, journal, databaseRecordsRef, globalSubscribersRef):

        var fetchData: Seq[Data] = hardcodedData

        ZStream.fromQueue(fetchQueue).mapZIO:
          id =>
            fetchData.find(_.id == id).map:
              data =>
                fetchData = fetchData.filterNot(_ eq data)
                for
                  now <- zio.Clock.instant
                  updatesFlaggedStatues <- databaseRecordsRef.update:
                    database =>
                      val etag = DataRecord.calculateEtag(data)
                      database.get(data.id) match
                        case Some(existing) if existing.etag == etag => database
                        case _ =>
                          val dataRecord = DataRecord(data, now, etag)
                          journal.publish(dataRecord).as(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, UPDATED, None))
                          database.updated(dataRecord.data.id, dataRecord)
                yield ()

            .getOrElse:
              ZIO.unit
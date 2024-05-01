package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.State.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, UpdateRequest}
import zio.stream.ZStream
import zio.{Hub, Queue, Ref, UIO, ULayer, ZIO, ZLayer}

import scala.collection.immutable.HashSet

object ExternalDataLayer:
  val live: ULayer[ExternalDataLayer] = ZLayer.succeed(ExternalDataLayer(Nil))

class ExternalDataLayer(hardcodedData: Seq[Data]):

  def createService(
                     journal: Hub[DataRecord],
                     databaseRecordsRef: Ref[Map[Int, DataRecord]],
                     globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                   ): UIO[ExternalDataService] =

    var fetchData: Seq[Data] = hardcodedData

    for
      fetchQueue <- Queue.unbounded[Int]
      _ <- ZStream.fromQueue(fetchQueue).mapZIO:
        id =>
          fetchData.find(_.id == id).map:
            data =>
              fetchData = fetchData.filterNot(_ eq data)
              for
                now <- zio.Clock.instant
                updatesDataRecordOption <- databaseRecordsRef.modify:
                  database =>
                    val etag = DataRecord.calculateEtag(data)
                    database.get(data.id) match
                      case Some(existing) if existing.etag == etag => (None, database)
                      case _ =>
                        val dataRecord = DataRecord(data, now, etag)
                        (Some(dataRecord), database.updated(dataRecord.data.id, dataRecord))
                _ <- updatesDataRecordOption match
                  case Some(dataRecord) => journal.publish(dataRecord)
                  case _ => ZIO.unit
              yield ()
          .getOrElse:
            ZIO.unit
      .runDrain
      .fork
    yield
      ExternalDataService(fetchQueue, journal, databaseRecordsRef, globalSubscribersRef)
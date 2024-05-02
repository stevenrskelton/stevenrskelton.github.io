package ca.stevenskelton.examples.realtimeziohubgrpc.commands

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.State.{CONFLICT, UNCHANGED, UPDATED}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{UpdateRequest, UpdateResponse}
import zio.{Hub, Ref, UIO, ZIO}

object DatabaseUpdate:

  def process(request: UpdateRequest, journal: Hub[DataRecord], databaseRecordsRef: Ref[Map[Int, DataRecord]]): UIO[UpdateResponse] =
    for
      now <- zio.Clock.instant
      updatesFlaggedStatues <- databaseRecordsRef.modify:
        database =>
          val updateStatuses = request.updates
            .flatMap:
              dataUpdate => dataUpdate.data.map((_, dataUpdate.previousEtag))
            .map:
              (data, previousETag) =>
                val updateETag = DataRecord.calculateEtag(data)
                database.get(data.id) match
                  case Some(existing) if existing.etag == updateETag => (existing, UNCHANGED)
                  case Some(existing) if existing.etag != previousETag => (existing, CONFLICT)
                  case _ => (DataRecord(data, now, updateETag), UPDATED)

          val updates = updateStatuses.withFilter(_._2 == UPDATED).map:
            (dataRecord, _) => dataRecord.data.id -> dataRecord

          (updateStatuses, database ++ updates)

      dataUpdateStatuses <- ZIO.collectAll:
        updatesFlaggedStatues.map:
          case (dataRecord, UNCHANGED) =>
            ZIO.succeed(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, UNCHANGED, None))
          case (dataRecord, CONFLICT) =>
            ZIO.succeed(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, CONFLICT, Some(dataRecord.data)))
          case (dataRecord, UPDATED) =>
            journal.publish(dataRecord).as(DataUpdateStatus.of(dataRecord.data.id, dataRecord.etag, UPDATED, None))
    yield
      UpdateResponse.of(updateStatuses = dataUpdateStatuses)


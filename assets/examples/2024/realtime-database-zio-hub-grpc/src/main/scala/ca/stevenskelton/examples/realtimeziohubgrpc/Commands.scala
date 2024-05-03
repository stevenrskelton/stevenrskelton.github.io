package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse}
import io.grpc.StatusException
import zio.stream.{Stream, ZStream}
import zio.{Hub, Ref, Scope, UIO, ZIO}

import scala.collection.immutable.HashSet

object Commands:

  private val HubMaxChunkSize = 1000

  /**
   * Create Stream from database `journal`
   */
  def userSubscriptionStream(userSubscriptionsRef: Ref[HashSet[DataId]], journal: Hub[DataRecord]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(journal, HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord => userSubscriptionsRef.get.map(_.contains(dataRecord.data.id))
      .map:
        dataRecord => SyncResponse.of(dataRecord.data.id, dataRecord.etag, Some(dataRecord.data), SyncResponse.State.UPDATED)

  /**
   * Update `databaseRecordsRef` with data in `request`, rejecting any conflicts
   * Emit changes to `journal`.
   */
  def updateDatabaseRecords(request: UpdateRequest, journal: Hub[DataRecord], databaseRecordsRef: Ref[Map[DataId, DataRecord]]): UIO[UpdateResponse] =
    import UpdateResponse.State.{CONFLICT, UNCHANGED, UPDATED}
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


  /**
   * Update `userSubscriptionsRef` with subscription changes.
   */
  def modifyUserSubscriptions(syncRequest: SyncRequest, userSubscriptionsRef: Ref[HashSet[DataId]], databaseRecords: Map[DataId, DataRecord]): UIO[Seq[SyncResponse]] =
    userSubscriptionsRef.modify:
      originalSubscribedIds =>

        var varSubscribedIds: HashSet[Int] = originalSubscribedIds

        val unsubscribedIds =
          if syncRequest.unsubscribes.isEmpty then Nil
          else if syncRequest.unsubscribes.headOption.exists(_.all) then
            varSubscribedIds = HashSet.empty
            originalSubscribedIds.toSeq.map((_, true))
          else
            val (removed, remaining) = originalSubscribedIds.partition(id => syncRequest.unsubscribes.exists(_.id == id))
            varSubscribedIds = remaining
            removed.toSeq.map((_, true)) ++ syncRequest.unsubscribes.withFilter(o => !removed.contains(o.id)).map(o => (o.id, false))

        val unsubscribedResponses = unsubscribedIds
          .withFilter((id, _) => syncRequest.subscribes.forall(_.id != id))
          .map:
            case (id, true) => SyncResponse.of(id, "", None, SyncResponse.State.UNSUBSCRIBED)
            case (id, false) => SyncResponse.of(id, "", None, SyncResponse.State.NOT_SUBSCRIBED)

        val subscribedResponses =
          if syncRequest.subscribes.isEmpty then Nil
          else
            syncRequest.subscribes.map:
              subscribe =>
                varSubscribedIds = varSubscribedIds.incl(subscribe.id)
                databaseRecords.get(subscribe.id) match
                  case Some(existing) if existing.etag == subscribe.previousEtag =>
                    SyncResponse.of(subscribe.id, existing.etag, None, SyncResponse.State.UNCHANGED)
                  case Some(existing) =>
                    SyncResponse.of(subscribe.id, existing.etag, Some(existing.data), SyncResponse.State.UPDATED)
                  case None =>
                    SyncResponse.of(subscribe.id, "", None, SyncResponse.State.NOT_FOUND)

        (unsubscribedResponses ++ subscribedResponses, varSubscribedIds)
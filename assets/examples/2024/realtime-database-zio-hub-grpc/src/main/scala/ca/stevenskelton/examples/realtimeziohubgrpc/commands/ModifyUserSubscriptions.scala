package ca.stevenskelton.examples.realtimeziohubgrpc.commands

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncResponse.State.{NOT_FOUND, NOT_SUBSCRIBED, UNCHANGED, UNSUBSCRIBED, UPDATED}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse}
import zio.{Ref, UIO}

import scala.collection.immutable.HashSet

object ModifyUserSubscriptions:

  def process(syncRequest: SyncRequest, userSubscriptionsRef: Ref[HashSet[Int]], databaseRecords: Map[Int, DataRecord]): UIO[Seq[SyncResponse]] =
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
            case (id, true) => SyncResponse.of(id, "", None, UNSUBSCRIBED)
            case (id, false) => SyncResponse.of(id, "", None, NOT_SUBSCRIBED)

        val subscribedResponses =
          if syncRequest.subscribes.isEmpty then Nil
          else
            syncRequest.subscribes.map:
              subscribe =>
                varSubscribedIds = varSubscribedIds.incl(subscribe.id)
                databaseRecords.get(subscribe.id) match
                  case Some(existing) if existing.etag == subscribe.previousEtag =>
                    SyncResponse.of(subscribe.id, existing.etag, None, UNCHANGED)
                  case Some(existing) =>
                    SyncResponse.of(subscribe.id, existing.etag, Some(existing.data), UPDATED)
                  case None =>
                    SyncResponse.of(subscribe.id, "", None, NOT_FOUND)

        (unsubscribedResponses ++ subscribedResponses, varSubscribedIds)
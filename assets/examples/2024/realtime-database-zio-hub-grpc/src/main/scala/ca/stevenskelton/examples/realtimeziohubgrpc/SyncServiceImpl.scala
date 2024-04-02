package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.{Connect, Subscribe, Unsubscribe, Update}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, ZioSyncService}
import io.grpc.{Status, StatusException}
import zio.stream.{Stream, ZStream}
import zio.{Hub, Ref, ZIO}

import java.time.Instant
import scala.collection.mutable

class SyncServiceImpl(
                       hub: Hub[DataInstant],
                       database: Ref[mutable.Map[Int, DataInstant]],
                       streamFilters: Ref[mutable.Map[AuthenticatedUser, DataStreamFilter]],
                     ) extends ZioSyncService.ZSyncService[AuthenticatedUser] {

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] = {
    request.flatMap {
      syncRequest =>
        syncRequest.action match {
          case SyncRequest.Action.Connect(connect) => handleConnect(syncRequest.getConnect, context)
          case SyncRequest.Action.Subscribe(subscribe) => handleSubscribe(subscribe, context)
          case SyncRequest.Action.Unsubscribe(unsubscribe) => handleUnsubscribe(unsubscribe, context)
          case SyncRequest.Action.Update(update) => handleUpdate(update, context)
          case SyncRequest.Action.Empty => ZStream.empty
        }
      //      case syncRequest if syncRequest.action.isConnect =>
      //      case syncRequest if syncRequest.action.isSubscribe => handleSubscribe(syncRequest.getSubscribe, context)
      //      case syncRequest if syncRequest.action.isUnsubscribe => handleUnsubscribe(syncRequest.getUnsubscribe, context)
      //      case syncRequest if syncRequest.action.isUpdate => handleUpdate(syncRequest.getUpdate, context)
    } ++ ZStream.finalizer {
      streamFilters.get.map(_.remove(context))
    }.map(_ => SyncResponse(data = None))
  }

  def handleConnect(connect: Connect, context: AuthenticatedUser): Stream[StatusException, SyncResponse] = {
    ZStream.fromHub(hub, SyncServer.HubMaxChunkSize)
      .filterZIO {
        dataInstant =>
          streamFilters.get
            .map(_.getOrElse(context, DataStreamFilter.Empty))
            .map(_.isWatching(dataInstant.data))
      }
      .map {
        dataInstant => SyncResponse.of(data = Some(dataInstant.data), lastUpdate = dataInstant.lastUpdate.getEpochSecond)
      }
  }

  def handleSubscribe(subscribe: Subscribe, context: AuthenticatedUser): Stream[StatusException, SyncResponse] = {
    val z1 = streamFilters.modifySome(ZIO.fail(StatusException(Status.FAILED_PRECONDITION))) {
      case m if m.contains(context) =>
        val filters = m(context)
        val z = database.get.map {
          dataMap =>
            subscribe.dataSnapshots.flatMap {
              dataSnapshot =>
                val _ = filters.subscribe(dataSnapshot.id)
                dataMap.get(dataSnapshot.id).filterNot(_.lastUpdate.isBefore(Instant.ofEpochSecond(dataSnapshot.lastUpdate)))
            }
        }
        (z, m)
    }
    ZStream.fromIterableZIO(z1.flatten).map(dataInstant => SyncResponse.of(data = Some(dataInstant.data), lastUpdate = dataInstant.lastUpdate.getEpochSecond))
  }

  def handleUnsubscribe(unsubscribe: Unsubscribe, context: AuthenticatedUser): Stream[StatusException, SyncResponse] = {
    ZStream.fromZIO(streamFilters.update {
      m =>
        val _ = m.get(context).map(filter => filter.unsubscribeAll(unsubscribe.ids))
        m
    }).map(_ => SyncResponse.defaultInstance)
  }

  def handleUpdate(update: Update, context: AuthenticatedUser): Stream[StatusException, SyncResponse] = {
    val z = for
      now <- zio.Clock.instant
      conflicts <- database.modify {
        m =>
          val conflicts = update.data.flatMap {
            data =>
              m.get(data.id) match {
                case o@Some(existing) if existing.lastUpdate.isAfter(now) => o
                case _ =>
                  val _ = m.update(data.id, DataInstant(data, now))
                  None
              }
          }
          (conflicts, m)
      }
    yield {
      conflicts.map {
        conflict => SyncResponse.of(data = Some(conflict.data), lastUpdate = conflict.lastUpdate.getEpochSecond)
      }
    }
    ZStream.fromIterableZIO(z)
  }
}

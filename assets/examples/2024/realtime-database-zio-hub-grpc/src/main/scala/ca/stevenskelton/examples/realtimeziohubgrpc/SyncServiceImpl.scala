package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.{Subscribe, Unsubscribe, Update}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, ZioSyncService}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, Scope, ZIO}

import java.time.Instant
import scala.collection.mutable

class SyncServiceImpl(
                       hub: Hub[DataInstant],
                       database: Ref[mutable.Map[Int, DataInstant]],
                     ) extends ZioSyncService.ZSyncService[AuthenticatedUser] {

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] = {
    ZStream.unwrapScoped {
      for {
        dataStreamFilterRef <- initializeUser(context)
        hubStream <- createHubStream(dataStreamFilterRef)
      } yield {
        val requestStreams = request.flatMap {
          syncRequest =>
            ZStream.unwrap {
              ZIO.log(s"User${context.id} Request: $syncRequest") *> ZIO.succeed {
                val stream = syncRequest.action match {
                  case SyncRequest.Action.Subscribe(subscribe) => handleSubscribe(subscribe, dataStreamFilterRef)
                  case SyncRequest.Action.Unsubscribe(unsubscribe) => handleUnsubscribe(unsubscribe, dataStreamFilterRef)
                  case SyncRequest.Action.Update(update) => handleUpdate(update)
                  case SyncRequest.Action.Empty => ZStream.empty
                }
                stream.onError {
                  ex => ZIO.log(s"Exception on request ${syncRequest} for user-${context.id}: ${ex.prettyPrint}")
                }
              }.map {
                t =>
                  t.tap {
                    o => ZIO.log(s"Stream user-${context.id}: ${o}")
                  }
              }
            }
        }

        val endOfRequestStream = ZStream.finalizer {
            ZIO.log(s"Finalizing user-${context.id}")
          }
          .map {
            _ => SyncResponse.of(data = None, lastUpdate = 0)
          }

        hubStream.merge(requestStreams ++ endOfRequestStream, strategy = HaltStrategy.Right)
      }
    }
  }

  private def initializeUser(context: AuthenticatedUser): IO[StatusException, Ref[DataStreamFilter]] = {
    Ref.make(DataStreamFilter())
  }

  private def createHubStream(dataStreamFilterRef: Ref[DataStreamFilter]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] = {
    ZStream.fromHubScoped(hub, SyncServer.HubMaxChunkSize).debug
      .map {
        stream =>
          stream
            .filterZIO {
              dataInstant =>
                dataStreamFilterRef.get.map {
                  _.isWatching(dataInstant.data)
                }
            }
            .map {
              dataInstant =>
                SyncResponse.of(
                  data = Some(dataInstant.data),
                  lastUpdate = dataInstant.lastUpdate.getEpochSecond,
                )
            }
      }
  }

  private def handleSubscribe(subscribe: Subscribe, dataStreamFilterRef: Ref[DataStreamFilter]): Stream[StatusException, SyncResponse] = {
    val syncResponses = for {
      dataMap <- database.get
      sinceLastUpdate <- dataStreamFilterRef.get.map {
        dataStreamFilter =>
          subscribe.dataSnapshots.flatMap {
            dataSnapshot =>
              val _ = dataStreamFilter.subscribe(dataSnapshot.id)
              dataMap.get(dataSnapshot.id).filterNot(_.lastUpdate.isBefore(Instant.ofEpochSecond(dataSnapshot.lastUpdate)))
          }
      }
    } yield {
      sinceLastUpdate.map {
        dataInstant => SyncResponse.of(data = Some(dataInstant.data), lastUpdate = dataInstant.lastUpdate.getEpochSecond)
      }
    }
    ZStream.fromIterableZIO(syncResponses)
  }

  private def handleUnsubscribe(unsubscribe: Unsubscribe, dataStreamFilterRef: Ref[DataStreamFilter]): Stream[StatusException, SyncResponse] = {
    ZStream.fromZIO {
      dataStreamFilterRef.update {
        dataStreamFilter =>
          val _ = dataStreamFilter.unsubscribeAll(unsubscribe.ids)
          dataStreamFilter
      }.as(SyncResponse.defaultInstance)
    }
  }

  private def handleUpdate(update: Update): Stream[StatusException, SyncResponse] = {
    val z = for
      now <- zio.Clock.instant
      isConflicts <- database.modify {
        m =>
          val conflictUpdates = update.data.map {
            data =>
              m.get(data.id) match {
                case Some(existing) if existing.lastUpdate.isAfter(now) => (existing, true)
                case _ =>
                  val dataInstant = DataInstant(data, now)
                  val _ = m.update(data.id, dataInstant)
                  (dataInstant, false)
              }
          }
          (conflictUpdates, m)
      }
      conflicts <- ZIO.collectAll {
        isConflicts.map {
          case (dataInstant, true) => ZIO.succeed(Some(dataInstant))
          case (dataInstant, false) => hub.publish(dataInstant).as(None)
        }
      }
    yield {
      conflicts.flatten.map {
        conflict => SyncResponse.of(data = Some(conflict.data), lastUpdate = conflict.lastUpdate.getEpochSecond)
      }
    }
    ZStream.fromIterableZIO(z)
  }
}

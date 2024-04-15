package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, Scope, UIO, ZIO}

object ZSyncServiceImpl:

  private val HubCapacity = 1000
  private val HubMaxChunkSize = 1000

  def launch: UIO[ZSyncServiceImpl] =
    for
      dataJournal <- Hub.sliding[DataRecord](HubCapacity)
      databaseRef <- Ref.make[Map[Int, DataRecord]](Map.empty)
      activeSubscribersRef <- Ref.make[List[Ref[UserSubscriptionManager]]](List.empty)
    yield
      ZSyncServiceImpl(dataJournal, databaseRef, activeSubscribersRef)


class ZSyncServiceImpl private(
                                journal: Hub[DataRecord],
                                databaseRef: Ref[Map[Int, DataRecord]],
                                activeSubscribersRef: Ref[List[Ref[UserSubscriptionManager]]],
                              ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

  def subscribedIds: UIO[Set[Int]] =
    for
      managerRefList <- activeSubscribersRef.get
      managerList <- ZIO.collectAll(managerRefList.map(_.get))
    yield
      managerList.foldLeft(Set.empty)(_ ++ _.getSubscriptions)

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        subscriptionManager <- UserSubscriptionManager.create(context)
        subscriptionManagerRef <- Ref.make(subscriptionManager)
        updateStream <- createSubscriptionStream(subscriptionManagerRef)
        _ <- activeSubscribersRef.update(_ :+ subscriptionManagerRef)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.unwrap:
              subscriptionManagerRef.modify:
                subscriptionManager =>

                  import SyncResponse.State.{LOADING, NOT_SUBSCRIBED, UNCHANGED, UNSUBSCRIBED, UPDATED}

                  val unsubscribedIds =
                    if syncRequest.unsubscribeAll then subscriptionManager.removeSubscription(Nil)
                    else if syncRequest.unsubscribeIds.nonEmpty then subscriptionManager.removeSubscription(syncRequest.unsubscribeIds)
                    else Nil

                  val unsubscribedStream = ZStream.fromIterable:
                    unsubscribedIds
                      .withFilter((id, _) => syncRequest.subscribes.forall(_.id != id))
                      .map:
                        case (id, true) => SyncResponse.of(id, "", None, UNSUBSCRIBED)
                        case (id, false) => SyncResponse.of(id, "", None, NOT_SUBSCRIBED)

                  val subscribedStream = if syncRequest.subscribes.isEmpty then ZStream.empty else ZStream.fromIterableZIO:
                    for
                      dataMap <- databaseRef.get
                    yield
                      syncRequest.subscribes.map:
                        subscribe =>
                          val _ = subscriptionManager.subscribe(subscribe.id)
                          dataMap.get(subscribe.id) match
                            case Some(existing) if existing.etag == subscribe.previousEtag =>
                              SyncResponse.of(subscribe.id, existing.etag, None, UNCHANGED)
                            case Some(existing) =>
                              SyncResponse.of(subscribe.id, existing.etag, Some(existing.data), UPDATED)
                            case None =>
                              SyncResponse.of(subscribe.id, "", None, LOADING)

                  (unsubscribedStream ++ subscribedStream, subscriptionManager)

        val endOfAllRequestsStream = ZStream
          .finalizer:
            activeSubscribersRef.update:
              _.filter(_ != subscriptionManagerRef)
            *> ZIO.log(s"Finalizing user-${context.userId}")
          .drain

        updateStream.merge(requestStreams ++ endOfAllRequestsStream, strategy = HaltStrategy.Right)

  end bidirectionalStream


  private def createSubscriptionStream(subscriptionManagerRef: Ref[UserSubscriptionManager]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(journal, ZSyncServiceImpl.HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord =>
          subscriptionManagerRef.get.map:
            _.isWatching(dataRecord.data)
      .map:
        dataRecord => SyncResponse.of(dataRecord.data.id, dataRecord.etag, Some(dataRecord.data), SyncResponse.State.UPDATED)

  override def update(request: UpdateRequest, context: AuthenticatedUser): IO[StatusException, UpdateResponse] =
    import UpdateResponse.State.{CONFLICT, UNCHANGED, UPDATED}
    for
      now <- zio.Clock.instant
      updatesFlaggedStatues <- databaseRef.modify:
        database =>
          val updateStatuses = request.updates
            .flatMap:
              dataUpdate => dataUpdate.data.map((_, dataUpdate.previousEtag))
            .map:
              (data, previousEtag) =>
                val updateETag = DataRecord.calculateEtag(data)
                database.get(data.id) match
                  case Some(existing) if existing.etag == updateETag => (existing, UNCHANGED)
                  case Some(existing) if existing.etag != previousEtag => (existing, CONFLICT)
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

  end update
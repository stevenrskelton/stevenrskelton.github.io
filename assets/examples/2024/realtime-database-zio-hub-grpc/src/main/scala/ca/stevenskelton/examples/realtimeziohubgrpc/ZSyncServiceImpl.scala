package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.UpdateResponse.DataUpdateStatus
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Ref, Scope, UIO, ZIO}

import scala.collection.immutable.HashSet

object ZSyncServiceImpl:

  private val HubCapacity = 1000
  private val HubMaxChunkSize = 1000

  def launch: UIO[ZSyncServiceImpl] =
    for
      dataJournal <- Hub.sliding[DataRecord](HubCapacity)
      databaseRef <- Ref.make[Map[Int, DataRecord]](Map.empty)
      globalSubscribersRef <- Ref.make[Set[Ref[HashSet[Int]]]](Set.empty)
      externalData <- ExternalData.create(dataJournal, databaseRef)
    yield
      ZSyncServiceImpl(dataJournal, databaseRef, globalSubscribersRef, externalData)


class ZSyncServiceImpl private(
                                journal: Hub[DataRecord],
                                databaseRef: Ref[Map[Int, DataRecord]],
                                globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                                externalData: ExternalData,
                              ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

  def subscribedIds: UIO[Set[Int]] =
    for
      globalSubscribers <- globalSubscribersRef.get
      subscribedIdSets <- ZIO.collectAll(globalSubscribers.map(_.get))
    yield
      subscribedIdSets.flatten

  override def bidirectionalStream(request: Stream[StatusException, SyncRequest], context: AuthenticatedUser): Stream[StatusException, SyncResponse] =
    ZStream.unwrapScoped:
      for
        userSubscriptionsRef <- Ref.make(HashSet.empty[Int])
        updateStream <- createUserSubscriptionStream(userSubscriptionsRef)
        _ <- globalSubscribersRef.update(_ + userSubscriptionsRef)
      yield
        val requestStreams = request.flatMap:
          syncRequest =>
            ZStream.fromIterableZIO:
              databaseRef.get.flatMap:
                dataMap => modifyUserSubscriptionsRef(syncRequest, userSubscriptionsRef, dataMap)
              .tap:
                responses => externalData.queueFetchAll(responses.withFilter(_.state == SyncResponse.State.LOADING).map(_.id))

        val endOfAllRequestsStream = ZStream
          .finalizer:
            globalSubscribersRef.update:
              _.filter(_ != userSubscriptionsRef)
            *> ZIO.log(s"Finalizing user-${context.userId}")
          .drain

        updateStream.merge(requestStreams ++ endOfAllRequestsStream, strategy = HaltStrategy.Right)

  end bidirectionalStream

  private def modifyUserSubscriptionsRef(syncRequest: SyncRequest, userSubscriptionsRef: Ref[HashSet[Int]], dataMap: Map[Int, DataRecord]): UIO[Seq[SyncResponse]] = {
    userSubscriptionsRef.modify:
      originalSubscribedIds =>

        var varSubscribedIds: HashSet[Int] = originalSubscribedIds

        import SyncResponse.State.{LOADING, NOT_SUBSCRIBED, UNCHANGED, UNSUBSCRIBED, UPDATED}

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

        val subscribedResponses = if syncRequest.subscribes.isEmpty then Nil else
          syncRequest.subscribes.map:
            subscribe =>
              varSubscribedIds = varSubscribedIds.incl(subscribe.id)
              dataMap.get(subscribe.id) match
                case Some(existing) if existing.etag == subscribe.previousEtag =>
                  SyncResponse.of(subscribe.id, existing.etag, None, UNCHANGED)
                case Some(existing) =>
                  SyncResponse.of(subscribe.id, existing.etag, Some(existing.data), UPDATED)
                case None =>
                  SyncResponse.of(subscribe.id, "", None, LOADING)

        (unsubscribedResponses ++ subscribedResponses, varSubscribedIds)
  }

  private def createUserSubscriptionStream(userSubscriptionsRef: Ref[HashSet[Int]]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(journal, ZSyncServiceImpl.HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord =>
          userSubscriptionsRef.get.map(_.contains(dataRecord.data.id))
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
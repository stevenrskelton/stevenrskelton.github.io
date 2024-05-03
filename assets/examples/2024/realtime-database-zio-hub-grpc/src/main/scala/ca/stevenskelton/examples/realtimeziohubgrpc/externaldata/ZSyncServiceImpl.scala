package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.commands.ModifyUserSubscriptions
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse, UpdateRequest, UpdateResponse, ZioSyncService}
import ca.stevenskelton.examples.realtimeziohubgrpc.{AuthenticatedUser, DataRecord}
import io.grpc.StatusException
import zio.stream.ZStream.HaltStrategy
import zio.stream.{Stream, ZStream}
import zio.{Clock, Hub, IO, Ref, Scope, URIO, ZIO}

import scala.collection.immutable.HashSet
import scala.collection.mutable

object ZSyncServiceImpl:

  private val HubCapacity = 1000
  private val HubMaxChunkSize = 1000

  def launch(initial: Seq[Data] = Nil): URIO[ExternalDataLayer, ZSyncServiceImpl] =
    ZIO.serviceWithZIO[ExternalDataLayer]:
      externalDataLayer =>
        for
          journal <- Hub.sliding[DataRecord](HubCapacity)
          now <- Clock.instant
          initialMap = initial.map(data => data.id -> DataRecord(data, now, DataRecord.calculateEtag(data))).toMap
          databaseRecordsRef <- Ref.make[Map[Int, DataRecord]](initialMap)
          globalSubscribersRef <- Ref.make[Set[Ref[HashSet[Int]]]](Set.empty)
          externalDataService <- externalDataLayer.createService(journal, databaseRecordsRef, globalSubscribersRef)
        yield
          ZSyncServiceImpl(journal, databaseRecordsRef, globalSubscribersRef, externalDataService)

case class ZSyncServiceImpl private(
                                     journal: Hub[DataRecord],
                                     databaseRecordsRef: Ref[Map[Int, DataRecord]],
                                     globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                                     externalDataService: ExternalDataService,
                                   ) extends ZioSyncService.ZSyncService[AuthenticatedUser]:

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
              databaseRecordsRef.get.flatMap:
                databaseRecords => ModifyUserSubscriptions.process(syncRequest, userSubscriptionsRef, databaseRecords)
              .flatMap:
                responses =>
                  val idsToFetch = new mutable.HashSet[Int]()
                  val loadingResponses = responses.map:
                    syncResponse =>
                      if syncResponse.state == SyncResponse.State.NOT_FOUND then
                        idsToFetch.add(syncResponse.id)
                        syncResponse.copy(state = SyncResponse.State.LOADING)
                      else syncResponse

                  if idsToFetch.nonEmpty then externalDataService.queueFetchAll(idsToFetch).as(loadingResponses)
                  else ZIO.succeed(loadingResponses)

        val endOfAllRequestsStream = ZStream.finalizer:
          globalSubscribersRef.update:
            _.filter(_ != userSubscriptionsRef)
          *> ZIO.log(s"Finalizing user-${context.userId}")
        .drain

        updateStream.merge(requestStreams ++ endOfAllRequestsStream, strategy = HaltStrategy.Right)


  private def createUserSubscriptionStream(userSubscriptionsRef: Ref[HashSet[Int]]): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]] =
    ZStream.fromHubScoped(journal, ZSyncServiceImpl.HubMaxChunkSize).map:
      _.filterZIO:
        dataRecord =>
          userSubscriptionsRef.get.map(_.contains(dataRecord.data.id))
      .map:
        dataRecord => SyncResponse.of(dataRecord.data.id, dataRecord.etag, Some(dataRecord.data), SyncResponse.State.UPDATED)

  override def update(request: UpdateRequest, context: AuthenticatedUser): IO[StatusException, UpdateResponse] = ???
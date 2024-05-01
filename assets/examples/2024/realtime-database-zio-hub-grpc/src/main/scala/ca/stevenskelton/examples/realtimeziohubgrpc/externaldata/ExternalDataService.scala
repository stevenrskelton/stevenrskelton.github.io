package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.commands.DatabaseUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{UpdateRequest, UpdateResponse}
import zio.stream.ZStream
import zio.{Chunk, Hub, Queue, Ref, UIO, ZIO}

import scala.collection.immutable.HashSet

class ExternalDataService(
                           fetchQueue: Queue[Int],
                           journal: Hub[DataRecord],
                           databaseRecordsRef: Ref[Map[Int, DataRecord]],
                           globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                                  ):

  def subscribedIds: UIO[Set[Int]] =
    for
      globalSubscribers <- globalSubscribersRef.get
      subscribedIdSets <- ZIO.collectAll(globalSubscribers.map(_.get))
    yield
      subscribedIdSets.flatten

  def queueFetchAll(ids: Iterable[Int]): UIO[Chunk[Int]] = fetchQueue.offerAll(ids)

  def update(request: UpdateRequest): UIO[UpdateResponse] = DatabaseUpdate.process(request, journal, databaseRecordsRef)
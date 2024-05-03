package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.commands.DatabaseUpdate
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{UpdateRequest, UpdateResponse}
import zio.{Chunk, Hub, Queue, Ref, UIO}

import scala.collection.immutable.HashSet

class ExternalDataService(
                           fetchQueue: Queue[Int],
                           journal: Hub[DataRecord],
                           databaseRecordsRef: Ref[Map[Int, DataRecord]],
                           globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                         ):

  def queueFetchAll(ids: Iterable[Int]): UIO[Chunk[Int]] = fetchQueue.offerAll(ids)

  def update(request: UpdateRequest): UIO[UpdateResponse] = DatabaseUpdate.process(request, journal, databaseRecordsRef)
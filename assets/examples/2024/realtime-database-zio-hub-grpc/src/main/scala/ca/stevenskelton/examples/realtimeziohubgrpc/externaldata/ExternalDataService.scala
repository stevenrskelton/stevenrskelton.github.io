package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{UpdateRequest, UpdateResponse}
import ca.stevenskelton.examples.realtimeziohubgrpc.{Commands, DataRecord}
import zio.{Chunk, Hub, Queue, Ref, UIO}

import scala.collection.immutable.HashSet

class ExternalDataService(
                           fetchQueue: Queue[DataId],
                           journal: Hub[DataRecord],
                           databaseRecordsRef: Ref[Map[DataId, DataRecord]],
                           globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]],
                         ):

  def queueFetchAll(ids: Iterable[DataId]): UIO[Chunk[DataId]] = fetchQueue.offerAll(ids)

  def update(request: UpdateRequest): UIO[UpdateResponse] = Commands.updateDatabaseRecords(request, journal, databaseRecordsRef)
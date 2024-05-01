package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import zio.{Chunk, Hub, Queue, Ref, UIO, ZIO}

import scala.collection.immutable.HashSet

class ExternalDataService(
                                    fetchQueue: Queue[Int],
                                    dataJournal: Hub[DataRecord],
                                    databaseRef: Ref[Map[Int, DataRecord]],
                                    globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
                                  ):

  def subscribedIds: UIO[Set[Int]] =
    for
      globalSubscribers <- globalSubscribersRef.get
      subscribedIdSets <- ZIO.collectAll(globalSubscribers.map(_.get))
    yield
      subscribedIdSets.flatten

  def queueFetchAll(ids: Seq[Int]): UIO[Chunk[Int]] = fetchQueue.offerAll(ids)

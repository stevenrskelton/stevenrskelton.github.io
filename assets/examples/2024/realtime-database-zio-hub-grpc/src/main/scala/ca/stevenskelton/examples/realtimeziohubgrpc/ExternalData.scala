package ca.stevenskelton.examples.realtimeziohubgrpc

import zio.{Chunk, Hub, Queue, Ref, UIO}

object ExternalData:
  def create(
              dataJournal: Hub[DataRecord],
              databaseRef: Ref[Map[Int, DataRecord]],
            ): UIO[ExternalData] =
    for
      fetchQueue <- Queue.unbounded[Int]
    yield
      ExternalData(fetchQueue, dataJournal, databaseRef)

class ExternalData(
                    fetchQueue: Queue[Int],
                    dataJournal: Hub[DataRecord],
                    databaseRef: Ref[Map[Int, DataRecord]],
                  ):

  def queueFetchAll(ids: Seq[Int]): UIO[Chunk[Int]] = fetchQueue.offerAll(ids)


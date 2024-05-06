---
title: "Realtime Client Database: External Datasource using ZIO ZLayer"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
example: realtime-database-zio-hub-grpc
sources:
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/Main.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImpl.scala"
  - "/src/test/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImplSpec.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ExternalDataLayer.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/HardcodedExternalDataLayer.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ClockExternalDataLayer.scala"
---

//TODO
<!--more-->

{% include multi_part_post.html %}

# External Data using ZLayer

```scala
/**
 * Data layer that will refresh all subscribed data an external datasource based on a schedule.
 *
 * @param refreshSchedule
 */
abstract class ExternalDataLayer(refreshSchedule: Schedule[Any, Any, Any]) {

  /**
   * Implementation details for resolving elements in queue to external `Data`.
   *
   * @param chunk Will contain the `DataRecord` for records existing in `databaseRecordsRef`
   * @return `Data` from external source when available.
   */
  protected def externalData(chunk: NonEmptyChunk[Either[DataId, DataRecord]]): UIO[Chunk[Data]]

  /**
   * All DataId from this queue are fetched from external datasource.
   * Updates are commited to `databaseRecordsRef` and emitted to `journal`.
   * Creation of queue will start the automatic data refresh using `refreshSchedule`.
   * Closing queue will stop the scheduled refresh.
   * Calling multiple times will execute schedules in parallel, 
   * useful for manual refresh using `Schedule.once`.
   */
  def createFetchQueue(
                        journal: Hub[DataRecord],
                        databaseRecordsRef: Ref[Map[DataId, DataRecord]],
                        globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]],
                      ): UIO[Enqueue[DataId]]

  /**
   * Whenever DataId are queued for refresh, call `externalData` to get data,
   * Update `databaseRecordsRef` when new data available without conflicts,
   * Emit data updates to `journal`.
   */
  protected def attachFetchQueueListener(
                                          queue: Dequeue[DataId],
                                          journal: Hub[DataRecord],
                                          databaseRecordsRef: Ref[Map[DataId, DataRecord]]
                                        ): UIO[Unit]

  /**
   * On schedule, attempt to refresh all subscribed data.
   */
  protected def attachRefreshScheduler(
                                        queue: Enqueue[DataId],
                                        globalSubscribersRef: Ref[Set[Ref[HashSet[DataId]]]]
                                      ): UIO[Unit]
}
```

## Hardcoded Data Implementation

A sample implementation for testing would be one which allows data to be specified on instantiation of the ZLayer, and 
then consumed when called with the appropriate id. To maintain data order, calls to `externalData` require single 
concurrency. In the Java thread paradigm this is done using `syncronize`, in ZIO `Ref.Synchronized` serves the same 
purpose.

```scala
class HardcodedExternalDataLayer private(hardcodedData: Ref.Synchronized[Seq[Data]], refreshSchedule: Schedule[Any, Any, Any])
  extends ExternalDataLayer(refreshSchedule) {

  override protected def externalData(
                                       chunk: NonEmptyChunk[Either[DataId, DataRecord]]
                                     ): UIO[Chunk[Data]] = {
    hardcodedData.modify {
      fetchData =>
        chunk.foldLeft((Chunk.empty[Data], fetchData))({
          case ((foldData, foldFetchData), either) =>
            val dataId = either.fold(identity, _.data.id)
            foldFetchData.find(_.id == dataId).map {
              data => (foldData :+ data, foldFetchData.filterNot(_ eq data))
            }.getOrElse {
              (foldData, foldFetchData)
            }
        })
    }
  }
}
```

## Performance Testing Implementation

A sample implementation for performance testing would timestamp on `Data` element updates allowing clients to compare 
time lag between server updates and their notification of it.  

Performance testing implementation and results are covered in [Realtime Client Database Performance Testing]({% post_url
2024-05-06-realtime-client-database-performance-testing %}).  

```scala
class ClockExternalDataLayer private(clock: Clock, refreshSchedule: Schedule[Any, Any, Any])
  extends ExternalDataLayer(refreshSchedule) {

  override protected def externalData(
                                       chunk: NonEmptyChunk[Either[DataId, DataRecord]]
                                     ): UIO[Chunk[Data]] = {
    clock.instant.map {
      now =>
        val dataId = either.fold(identity, _.data.id)
        chunk.map(either => Data.of(dataId, now.toString))
    }
  }
}
```


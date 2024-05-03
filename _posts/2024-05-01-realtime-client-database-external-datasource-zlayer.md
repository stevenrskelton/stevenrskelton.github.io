---
title: "Realtime Client Database with External Datasource using ZIO ZLayer"
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
---

//TODO

{%
include multi_part_post.html
series="Realtime Client Database"
p1="2024-04-15-realtime-client-database-grpc-streams-zio"
p2="2024-05-01-realtime-client-database-external-datasource-zlayer"
%}

# External Data using ZLayer

```scala
/**
 * Data layer that will refresh all subscribed data an external datasource based on a schedule.
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

## Hardcoded Implementation


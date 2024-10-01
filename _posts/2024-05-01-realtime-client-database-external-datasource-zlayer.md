---
title: "Realtime Client Database: External Datasource using ZIO ZLayer"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
  - gRPC
excerpt_separator: <!--more-->
example: realtime-database-zio-hub-grpc
sources:
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/Main.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImpl.scala"
  - "/src/test/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImplSpec.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ExternalDataLayer.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/HardcodedExternalDataLayer.scala"
---

Expanding on the realtime Firebase implementation in the previous article, this expands the functionality allowing the
server to fetch data on-demand from an external datasource. Additionally, functionality to periodical refresh active
data which is subscribed to by connected clients transforms this database into an efficient cache to evolving external
data which can only be obtained by polling.<!--more-->

{% include multi_part_post.html %}

{% include table-of-contents.html height="100px" %}

# External Data using a ZLayer

The ZLayer mechanism in ZIO conveniently defines environment dependencies, and will be used to create an external data
service implementation. The implementation details will depend on how and where the external data is stored, but it
will be exposed with a simple interface mapping data ids to new data elements.

The ZLayer _trait_ will contain the related methods used to transform inputs and outputs, as well as scheduling
updates. With _protected_ visibility implementations can be overriden accomidating non-standard operation.

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

## External Data Sample Implementations

### Hardcoded Data

A sample implementation useful for testing will allow data to be specified as a construction parameter. The data will
be part of the ZLayer instance with it consumed by calls from the server. This will allow subsequent calls to return
different data values allowing for state-based unit testing. However this requires strict ordering, so calls
to `externalData` requires single concurrency. Java threading uses `syncronize` for serialize execution, in ZIO fiber
synchronization is through `Ref.Synchronized`.

```scala
class HardcodedExternalDataLayer private(
                                          hardcodedData: Ref.Synchronized[Seq[Data]],
                                          refreshSchedule: Schedule[Any, Any, Any]
                                        )
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


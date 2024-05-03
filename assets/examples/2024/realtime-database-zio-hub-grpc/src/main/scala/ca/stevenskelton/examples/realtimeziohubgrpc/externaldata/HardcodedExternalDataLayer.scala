package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data
import zio.{Chunk, NonEmptyChunk, Ref, Schedule, UIO, ULayer, ZLayer}

object HardcodedExternalDataLayer:
  def live(hardcodedData: Seq[Data], refreshSchedule: Schedule[Any, Any, Any]): ULayer[HardcodedExternalDataLayer] =
    ZLayer.fromZIO:
      Ref.Synchronized.make(hardcodedData).map:
        HardcodedExternalDataLayer(_, refreshSchedule)

class HardcodedExternalDataLayer private(hardcodedData: Ref.Synchronized[Seq[Data]], refreshSchedule: Schedule[Any, Any, Any])
  extends ExternalDataLayer(refreshSchedule):

  override protected def externalData(chunk: NonEmptyChunk[Either[DataId, DataRecord]]): UIO[Chunk[Data]] =
    hardcodedData.modify:
      fetchData =>
        chunk.foldLeft((Chunk.empty[Data], fetchData)):
          case ((foldData, foldFetchData), either) =>
            val dataId = either.fold(identity, _.data.id)
            foldFetchData.find(_.id == dataId).map:
              data => (foldData :+ data, foldFetchData.filterNot(_ eq data))
            .getOrElse:
              (foldData, foldFetchData)

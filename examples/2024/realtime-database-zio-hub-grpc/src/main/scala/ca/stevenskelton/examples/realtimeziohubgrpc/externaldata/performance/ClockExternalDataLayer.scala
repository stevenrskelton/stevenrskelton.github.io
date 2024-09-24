package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata.performance

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.DataId
import ca.stevenskelton.examples.realtimeziohubgrpc.externaldata.ExternalDataLayer
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data
import zio.{Chunk, Clock, NonEmptyChunk, Schedule, UIO, ULayer, ZLayer}

object ClockExternalDataLayer:
  def live(clock: Clock, refreshSchedule: Schedule[Any, Any, Any]): ULayer[ClockExternalDataLayer] =
    ZLayer.succeed:
      ClockExternalDataLayer(clock, refreshSchedule)

class ClockExternalDataLayer private(clock: Clock, refreshSchedule: Schedule[Any, Any, Any])
  extends ExternalDataLayer(refreshSchedule):

  override protected def externalData(chunk: NonEmptyChunk[Either[DataId, DataRecord]]): UIO[Chunk[Data]] =
    for
      now <- clock.instant
    yield
      chunk.map:
        either => Data.of(either.fold(identity, _.data.id), now.toString)


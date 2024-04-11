package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord.ETag
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data

import java.time.Instant

case class DataRecord(data: Data, lastUpdate: Instant, etag: ETag)

object DataRecord:
  type ETag = String

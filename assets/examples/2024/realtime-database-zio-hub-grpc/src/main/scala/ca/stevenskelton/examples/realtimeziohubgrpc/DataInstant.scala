package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data

import java.time.Instant

case class DataInstant(data: Data, lastUpdate: Instant)

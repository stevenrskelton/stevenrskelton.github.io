package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, ZioSyncService}
import io.grpc.StatusException
import zio.stream

class SyncServiceImpl extends ZioSyncService.ZSyncService[AuthenticatedUser]{

  override def syncBidirectionalStream(request: stream.Stream[StatusException, SyncRequest], context: AuthenticatedUser): stream.Stream[StatusException, SyncResponse] = {
    ???
    
  }
}

package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.*
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{Metadata, ServerBuilder, Status, StatusException}
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{Cause, ExitCode, IO, URIO, ZIO, ZIOAppDefault}

object SyncServer extends ZIOAppDefault:

  private def authenticatedUserContext(requestContext: RequestContext): IO[StatusException, AuthenticatedUser] =
    requestContext.metadata.get(Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER)).flatMap:
      _.filterNot(_.isBlank)
        .flatMap(_.toIntOption)
        .map(id => ZIO.succeed(AuthenticatedUser(id)))
        .getOrElse:
          ZIO.logCause(Cause.fail(Exception("Missing or empty user id")))
            *> ZIO.fail(StatusException(Status.UNAUTHENTICATED))

  override def run: URIO[Any, ExitCode] =
    ServerLayer.fromServiceList(
        ServerBuilder.forPort(9000).addService(ProtoReflectionService.newInstance()),
        ServiceList.add(SyncServiceImpl().transformContextZIO(authenticatedUserContext)),
      )
      .launch
      .forever
      .exitCode

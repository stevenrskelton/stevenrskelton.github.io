package ca.stevenskelton.examples.realtimeziohubgrpc

import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{Metadata, ServerBuilder, Status, StatusException}
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{Cause, ExitCode, IO, URIO, ZIO, ZIOAppDefault}

object SyncServer extends ZIOAppDefault:

  val MetadataUserIdKey = "user-id"

  private val GRPCServerPort = 9000

  def authenticatedUserContext(requestContext: RequestContext): IO[StatusException, AuthenticatedUser] =
    requestContext.metadata.get(Metadata.Key.of(MetadataUserIdKey, Metadata.ASCII_STRING_MARSHALLER)).flatMap:
      _.filterNot(_.isBlank)
        .flatMap(_.toIntOption)
        .map(userId => ZIO.succeed(AuthenticatedUser(userId)))
        .getOrElse:
          ZIO.logCause("authenticatedUserContext", Cause.fail(Exception(s"Missing or empty $MetadataUserIdKey")))
            *> ZIO.fail(StatusException(Status.UNAUTHENTICATED))

  override def run: URIO[Any, ExitCode] =
    val app = for
      zSyncServiceImpl <- ZSyncServiceImpl.launch
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(zSyncServiceImpl.transformContextZIO(authenticatedUserContext)),
        )
        .launch
    yield
      grpcServer

    app.forever.exitCode
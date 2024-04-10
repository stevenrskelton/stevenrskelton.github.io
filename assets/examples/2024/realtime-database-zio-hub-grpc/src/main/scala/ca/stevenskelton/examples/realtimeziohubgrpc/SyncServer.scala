package ca.stevenskelton.examples.realtimeziohubgrpc

import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{Metadata, ServerBuilder, Status, StatusException}
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{Cause, ExitCode, Hub, IO, Ref, URIO, ZIO, ZIOAppDefault}

import scala.collection.mutable

object SyncServer extends ZIOAppDefault:

  val HubCapacity = 1000
  val HubMaxChunkSize = 1000
  val GRPCServerPort = 9000
  val MetadataUserIdKey = "user-id"

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
      hub <- Hub.sliding[DataInstant](HubCapacity)
      database <- Ref.make[mutable.Map[Int, DataInstant]](mutable.Map.empty)
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(ZSyncServiceImpl(hub, database).transformContextZIO(authenticatedUserContext)),
        )
        .launch
    yield
      grpcServer

    app.forever.exitCode
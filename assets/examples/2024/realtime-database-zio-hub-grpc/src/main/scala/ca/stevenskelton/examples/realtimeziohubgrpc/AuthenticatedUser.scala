package ca.stevenskelton.examples.realtimeziohubgrpc

import io.grpc.{Metadata, ServerBuilder, Status, StatusException}
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{Cause, ExitCode, IO, URIO, ZIO, ZIOAppDefault}

case class AuthenticatedUser(userId: AuthenticatedUser.UserId) extends AnyVal

object AuthenticatedUser:
  val MetadataUserIdKey = "user-id"
  
  type UserId = Int

  def context(requestContext: RequestContext): IO[StatusException, AuthenticatedUser] =
    requestContext.metadata.get(Metadata.Key.of(MetadataUserIdKey, Metadata.ASCII_STRING_MARSHALLER)).flatMap:
      _.filterNot(_.isBlank)
        .flatMap(_.toIntOption)
        .map(userId => ZIO.succeed(AuthenticatedUser(userId)))
        .getOrElse:
          ZIO.logCause("authenticatedUserContext", Cause.fail(Exception(s"Missing or empty $MetadataUserIdKey")))
            *> ZIO.fail(StatusException(Status.UNAUTHENTICATED))

package ca.stevenskelton.examples.realtimeziohubgrpc

case class AuthenticatedUser(userId: AuthenticatedUser.UserId) extends AnyVal

object AuthenticatedUser {
  type UserId = Int
}

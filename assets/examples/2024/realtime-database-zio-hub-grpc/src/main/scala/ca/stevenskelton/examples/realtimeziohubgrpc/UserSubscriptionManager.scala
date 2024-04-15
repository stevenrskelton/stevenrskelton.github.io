package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data
import io.grpc.StatusException
import zio.{IO, ZIO}

import scala.collection.mutable

object UserSubscriptionManager:
  def create(authenticatedUser: AuthenticatedUser): IO[StatusException, UserSubscriptionManager] =
    ZIO.succeed(UserSubscriptionManager(authenticatedUser))

case class UserSubscriptionManager private(authenticatedUser: AuthenticatedUser):

  private val watching = new mutable.HashSet[Int]

  def subscribe(id: Int): Boolean = watching.add(id)

  def removeSubscription(ids: Seq[Int]): Seq[(Int, Boolean)] =
    if (ids.isEmpty)
      val subscribed = watching.toSeq
      watching.clear()
      subscribed.map((_, true))
    else
      ids.map(id => (id, watching.remove(id)))

  def isWatching(data: Data): Boolean = watching.contains(data.id)

  def getSubscriptions: Set[Int] = watching.toSet


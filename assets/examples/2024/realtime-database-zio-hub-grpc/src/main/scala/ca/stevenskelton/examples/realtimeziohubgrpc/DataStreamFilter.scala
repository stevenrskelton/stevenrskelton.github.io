package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data

import scala.collection.mutable

class DataStreamFilter:

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


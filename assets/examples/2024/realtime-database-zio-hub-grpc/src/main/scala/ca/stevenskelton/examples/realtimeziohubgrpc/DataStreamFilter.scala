package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.Data

import scala.collection.mutable

class DataStreamFilter:

  private val watching = new mutable.HashSet[Int]

  def subscribe(id: Int): Boolean = watching.add(id)

  def unsubscribeAll(ids: IterableOnce[Int]): Unit = ids.iterator.foreach(watching.remove)

  def isWatching(data: Data): Boolean = watching.contains(data.id)


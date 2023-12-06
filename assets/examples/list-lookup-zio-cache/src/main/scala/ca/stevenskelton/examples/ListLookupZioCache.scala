package ca.stevenskelton.examples

import zio.cache.*

trait ListLookup[-Key, -Environment, +Error, +Value]{
  def lookupAll(key: Seq[Key]): ZIO[Environment, Error, Map[Key, Value]]
}

trait ListCache[-Key, +Error, +Value] extends Cache[Key, Error, Value] {
  def getAll(k: Seq[Key]): IO[Error, Map[Key, Value]]
}

object ListCache {

  def make[Key, Environment, Error, Value](
                                            capacity: Int,
                                            timeToLive: Duration,
                                            lookup: ListLookup[Key, Environment, Error, Value]
                                          ): ZIO[Environment, Nothing, ListCache[Key, Error, Value]] = {



    val internalCache: Cache[Key, Any, Error, Value] = Cache.make(capacity, timeToLive)

    val listCache = new ListCache[Key, Error, Value]{
      override def getAll(k: Seq[Key]): IO[Error, Map[Key, Value]] = {
        val (found, requireLookup) = k.partition(internalCache.contains)
        if(requireLookup.isEmpty) {
          ZIO.succeed(internalCache.g)
        } else {

        }
      }
    }
  }
}

class ListLookupZioCache {

}
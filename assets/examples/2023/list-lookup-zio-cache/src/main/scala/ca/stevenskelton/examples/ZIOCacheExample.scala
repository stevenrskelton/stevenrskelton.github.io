package ca.stevenskelton.examples

import zio._
import zio.cache.{Cache, Lookup}

//Adapted from https://github.com/zio/zio-cache
object ZIOCacheExample extends ZIOAppDefault {
  def timeConsumingEffect(keys: Seq[String]) = {
    val str = keys.mkString(",")
    val d = ZIO.debug(s"Lookup $str")
      d *> ZIO.sleep(3.seconds).as(keys.map(key => (key, key.hashCode)))
  }

  def run = {

    ListLookupCache.make(
      capacity = 100,
      timeToLive = Duration.Infinity,
      listLookup = ListLookup(timeConsumingEffect)
    ).flatMap {
      cache =>

//        val zSingle = for {
//          result <- cache
//            .get("key1")
//            .zipPar(cache.get("key1"))
//            .zipPar(cache.get("key1"))
//          _ <- ZIO.debug(
//            s"Result of parallel execution of three effects with the same key: $result"
//          )
//
//          hits <- cache.cacheStats.map(_.hits)
//          misses <- cache.cacheStats.map(_.misses)
//          _ <- ZIO.debug(s"Number of cache hits: $hits")
//          _ <- ZIO.debug(s"Number of cache misses: $misses")
//        } yield ()

        val zList = for {
          result <- cache
            .getAll(Seq("key1", "key2", "key3"))
            .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 2") *> cache.getAll(Seq("key1", "key2")))
            .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 3") *> cache.get("key1"))
            .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 4") *> cache.getAll(Seq("key1", "key4", "key6")))
            .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 5") *> cache.get("key5"))
          _ <- ZIO.debug(
            s"Result of parallel execution of three effects with the same key: $result"
          )

          hits <- cache.cacheStats.map(_.hits)
          misses <- cache.cacheStats.map(_.misses)
          _ <- ZIO.debug(s"Number of cache hits: $hits")
          _ <- ZIO.debug(s"Number of cache misses: $misses")
        } yield ()

//        zSingle *> zList
        zList

    }


  }

}
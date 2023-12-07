package ca.stevenskelton.examples

import zio.{IO, Trace, UIO, ZIO, Promise}
import zio.cache.{Cache, Lookup}

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

final case class ListLookup[Key, -Environment, +Error, +Value](lookupAll: Seq[Key] => ZIO[Environment, Error, Seq[(Key, Value)]])
  extends (Seq[Key] => ZIO[Environment, Error, Seq[(Key, Value)]]) {

  /**
   * Computes a value for the specified key or fails with an error.
   */
  def apply(keys: Seq[Key]): ZIO[Environment, Error, Seq[(Key, Value)]] = {
    lookupAll(keys)
  }
}

abstract class ListLookupCache[Key, +Error, +Value] extends Cache[Key, Error, Value] {
  def getAll(k: Seq[Key])(implicit trace: Trace): IO[Error, Map[Key, Value]]
}

object ListLookupCache {

  def make[Key, Environment, Error, Value](
                                            capacity: Int,
                                            timeToLive: Duration,
                                            listLookup: ListLookup[Key, Environment, Error, Value]
                                          )(implicit trace: Trace): ZIO[Environment, Nothing, ListLookupCache[Key, Error, Value]] = {

    ZIO.environment[Environment].flatMap { environment =>
      ZIO.fiberId.flatMap { fiberId =>
        val internalMap = new ConcurrentHashMap[Key, Promise[Error, Value]]()
        val internalLookup = Lookup[Key, Environment, Error, Value](
          key => Option(internalMap.remove(key))
            .map {
              promise => promise.await
            }
            .getOrElse {
              ZIO.debug(s"Single lookup $key") *> listLookup(Seq(key)).map(_.head._2)
            }
        )

        Cache.make(capacity, timeToLive, internalLookup).map {
          internalCache =>

            new ListLookupCache[Key, Error, Value] {

              export internalCache.get
              export internalCache.invalidate
              export internalCache.invalidateAll
              export internalCache.refresh
              export internalCache.size
              export internalCache.cacheStats
              export internalCache.entryStats
              export internalCache.contains

              override def getAll(keys: Seq[Key])(implicit trace: Trace): IO[Error, Map[Key, Value]] = {
                ZIO.collectAll(keys.map(internalCache.contains))
                  .map(_.forall(identity))
                  .flatMap {
                    case true =>
                      val z = keys.map { key => internalCache.get(key).map(value => (key, value)) }
                      ZIO.collectAll(z).map(Map.from)
                    case false =>
                      ZIO.suspendSucceedUnsafe { implicit u =>
                        val (keyPromises, keyValuesSeq) = keys.map {
                          key =>
                            val createdPromise = Promise.unsafe.make[Error, Value](fiberId)
                            val promise = Option(internalMap.putIfAbsent(key, createdPromise)).getOrElse(createdPromise)
                            println("Inserted all promise")
                            ((key, promise), internalCache.get(key).map((key, _)))
                        }.unzip
                        val zLookup = listLookup.lookupAll(keys)
                          .provideEnvironment(environment)
                          .flatMap {
                            keyValues =>
                              println(s"returned keyvalues ${keyValues.size}")
                              ZIO.collectAll(keyPromises.map {
                                kp => kp._2.succeed(keyValues.find(_._1 == kp._1).get._2)
                              })
                          }.catchAll {
                            ex =>
                              keyPromises.foreach {
                                kp => kp._2.fail(ex)
                              }
                              ZIO.debug(s"Lookup failed ${ex}") *> ZIO.fail(ex)
                          }
                        val zPopulate = ZIO.collectAll(keyValuesSeq).map(_.toMap)
                        zLookup &> zPopulate
                      }
                  }
              }
            }
        }
      }
    }
  }
}

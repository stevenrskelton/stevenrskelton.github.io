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

              def createPromise(key: Key): IO[Error, (Key, Promise[Error, Value])] = {
                Promise.make[Error, Value].flatMap {
                  createdPromise =>
                    val promise = Option(internalMap.putIfAbsent(key, createdPromise)).getOrElse(createdPromise)
                    internalCache.get(key).fork.as((key, promise))
                }
              }

              ZIO.collectAll(keys.map(key => internalCache.contains(key).map((key, _))))
                .map {
                  _.collect {
                    case (key, true) => internalCache.get(key)
                      .map(value => Left((key, value)))
                      .catchAll {
                        ex => createPromise(key).map(Right(_))
                      }
                    case (key, false) => createPromise(key).map(Right(_))
                  }
                }
                .flatMap(ZIO.collectAll)
                .flatMap {
                  keyPromises =>
                    val (fromCache, promisesToLoad) = keyPromises.partitionMap(identity)
                    if (promisesToLoad.isEmpty) ZIO.succeed(fromCache)
                    else {
                      val zLookup = listLookup.lookupAll(promisesToLoad.map(_._1))
                        .provideEnvironment(environment)
                        .flatMap {
                          keyValues =>
                            println(s"returned keyvalues ${keyValues.size}")
                            ZIO.collectAll(promisesToLoad.map {
                              kp =>
                                //TODO: handle missing
                                val value = keyValues.find(_._1 == kp._1).get._2
                                kp._2.succeed(value).map {
                                  _ => (kp._1, value)
                                }
                            })
                        }.catchAll {
                          ex =>
                            ZIO.collectAll(promisesToLoad.map(_._2.fail(ex)))
                              *> ZIO.debug(s"Lookup failed ${ex}")
                              *> ZIO.fail(ex)
                        }
                      zLookup.map(fromCache.++)
                    }
                }
                .map(_.toMap)
            }
          }
      }
    }
  }
}

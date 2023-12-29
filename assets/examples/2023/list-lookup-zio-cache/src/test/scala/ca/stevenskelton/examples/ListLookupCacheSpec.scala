package ca.stevenskelton.examples

import zio.*

import org.scalatest.*
import org.scalatest.flatspec.*
import org.scalatest.matchers.*

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

class ListLookupCacheSpec extends AnyFlatSpec with should.Matchers {

  "ListLookupCacheSpec" should "sayHello correctly displays output" in {

    val testCache = new TestCache(2.seconds)

    val zResults = for {
      cache <- testCache.cacheIO
      result <- cache
        .getAll(Seq("1", "2", "3"))
        .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 2") *> cache.getAll(Seq("1", "2")))
        .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 3") *> cache.get("1"))
        .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 4") *> cache.getAll(Seq("1", "4", "6")))
        .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 5") *> cache.get("5"))
      stats <- cache.cacheStats
    } yield (result, stats)

    val (results, cacheStats) = Await.result(Unsafe.unsafe {
      implicit u => zio.Runtime.default.unsafe.runToFuture(zResults).future
    }, FiniteDuration(15, TimeUnit.SECONDS))

    val (getAll1, getAll2, get3, getAll4, get5) = results

    getAll1.values.toSeq.sorted shouldEqual Seq(1, 2, 3)
    getAll2.values.toSeq.sorted shouldEqual Seq(1, 2)
    get3 shouldEqual 1
    getAll4.values.toSeq.sorted shouldEqual Seq(1, 4, 6)
    get5 shouldEqual 5

    cacheStats.size shouldEqual 6
    cacheStats.misses shouldEqual 6
    cacheStats.hits shouldEqual 4

    testCache.calls.size shouldEqual 3
    testCache.calls.toList shouldEqual List(Seq("1", "2", "3"), Seq("5"), Seq("4", "6"))
  }
}
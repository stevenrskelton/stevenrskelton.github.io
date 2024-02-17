package ca.stevenskelton.examples

import ca.stevenskelton.examples.ListLookupCacheZioSpec.{suite, test}
import zio.*
import zio.test.*

object ListLookupCacheZioSpec extends ZIOSpecDefault {

  def spec = suite("ListLookupCacheSpec")(

    test("sayHello correctly displays output") {
      val testCache = TestCache(2.seconds)

      for {
        cache <- testCache.cacheIO
        resultFork <- cache
          .getAll(Seq("1", "2", "3"))
          .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 2") *> cache.getAll(Seq("1", "2")))
          .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 3") *> cache.get("1"))
          .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 4") *> cache.getAll(Seq("1", "4", "6")))
          .zipPar(ZIO.sleep(1.second) *> ZIO.debug("Starting getAll 5") *> cache.get("5"))
          .fork
        _ <- TestClock.adjust(15.second)
        results <- resultFork.join
        cacheStats <- cache.cacheStats
      } yield {

        val (getAll1, getAll2, get3, getAll4, get5) = results
        assertTrue {
          getAll1.values.toSeq.sorted == Seq(1, 2, 3) &&
            getAll2.values.toSeq.sorted == Seq(1, 2) &&
            get3 == 1 &&
            getAll4.values.toSeq.sorted == Seq(1, 4, 6) &&
            get5 == 5 &&
            cacheStats.size == 6 &&
            cacheStats.misses == 6 &&
            cacheStats.hits == 4 &&
            testCache.calls.size == 3 &&
            testCache.calls.toList == List(Seq("1", "2", "3"), Seq("5"), Seq("4", "6"))
        }
      }
    }
  )
}
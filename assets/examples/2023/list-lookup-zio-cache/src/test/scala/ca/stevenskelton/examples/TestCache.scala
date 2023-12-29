package ca.stevenskelton.examples

import zio.{Duration, IO, ZIO}

import scala.collection.mutable.ListBuffer

case class TestCache(duration: Duration) {

  val calls: ListBuffer[Seq[String]] = new ListBuffer[Seq[String]]()

  val cacheIO: IO[Nothing, ListLookupCache[String, Nothing, Int]] = ListLookupCache.make(
    capacity = 100,
    timeToLive = Duration.Infinity,
    listLookup = ListLookup(timeConsumingEffect)
  )

  def timeConsumingEffect(keys: Seq[String]): IO[Nothing, Seq[(String, Int)]] = {
    calls.addOne(keys)
    val str = keys.mkString(",")
    for {
      _ <- ZIO.debug(s"Lookup $str")
      _ <- ZIO.sleep(duration)
    } yield {
      keys.map(key => (key, key.toInt))
    }
  }
}

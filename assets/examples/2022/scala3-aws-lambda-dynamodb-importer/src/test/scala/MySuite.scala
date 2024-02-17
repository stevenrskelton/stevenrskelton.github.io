import com.amazonaws.services.lambda.runtime.LambdaLogger

import scala.util.Using

// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
class MySuite extends munit.FunSuite {

  given lambdaLogger: LambdaLogger = new LambdaLogger {
    override def log(s: String): Unit = {}

    override def log(bytes: Array[Byte]): Unit = {}
  }

  val json = Using(scala.io.Source.fromResource("stockpriceitems.json"))(_.mkString).get

  test("parse json") {
    val stockPriceItems = parseStockPriceItems(json).get.toList
    assertEquals(stockPriceItems.size, 3)
    assertEquals(stockPriceItems.head.symbol, "SPY")
    assertEquals(stockPriceItems.head.time, "1660229200")
    assertEquals(stockPriceItems.head.prices, "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxOS43OCwicHJldmlvdXNDbG9zZSI6NDIwLjAwfQ==")
  }

}

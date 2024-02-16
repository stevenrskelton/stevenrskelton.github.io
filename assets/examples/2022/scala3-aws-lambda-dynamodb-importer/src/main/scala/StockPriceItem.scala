import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.protocols.jsoncore.JsonNode
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try

object StockPriceItem:
  private def parseField(jsonNode: JsonNode, fieldName: String, isNumber: Boolean = false): String =
    jsonNode.field(fieldName).map {
      node =>
        if (isNumber)
          Try(node.asNumber).toOption.getOrElse(throw ParseException(s"`$fieldName` is not a number", jsonNode.toString))
        else
          Try(node.asString).toOption.getOrElse(throw ParseException(s"`$fieldName` is not a string", jsonNode.toString))
    }.orElseThrow {
      () => ParseException(s"`$fieldName` not found", jsonNode.toString)
    }

  def apply(jsonNode: JsonNode): StockPriceItem = StockPriceItem(
    parseField(jsonNode, "symbol"),
    parseField(jsonNode, "time", isNumber = true),
    parseField(jsonNode, "prices")
  )

case class StockPriceItem(symbol: String, time: String, prices: String):
  val dynamoDBAttributeMap: java.util.Map[String, AttributeValue] =
    val pricesByteArray = try Base64.getDecoder.decode(prices) catch
      case _ => throw ParseException("could not decode base64", prices)
    Map(
      "symbol" -> AttributeValue.builder.s(symbol).build,
      "time" -> AttributeValue.builder.n(time).build,
      "prices" -> AttributeValue.builder.b(SdkBytes.fromByteArray(pricesByteArray)).build
    ).asJava

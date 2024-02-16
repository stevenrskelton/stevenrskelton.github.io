import com.amazonaws.services.lambda.runtime.events.{APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse}
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger, RequestHandler}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.protocols.jsoncore.{JsonNode, JsonNodeParser}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

import java.nio.file.Path
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.{Failure, Success, Try}

val tableName = "demo_stock_prices"

val dynamoDbClient = DynamoDbClient.builder
  .credentialsProvider(DefaultCredentialsProvider.create)
  .region(Region.US_EAST_1)
  .build

class Handler extends RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse] :

  override def handleRequest(event: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse =
    given lambdaLogger: LambdaLogger = context.getLogger

    lambdaLogger.log("Start")
    lambdaLogger.log(event.getBody)

    val body = Option(event.getBody).withFilter(!_.isBlank)
      .map(Success(_))
      .getOrElse(Failure(ParseException("empty body", "''")))

    body
      .flatMap(parseStockPriceItems)
      .flatMap(putIntoDynamoDB)
      .fold(errorToResult, count =>
        APIGatewayV2HTTPResponse.builder
          .withStatusCode(200)
          .withBody(s"""{ "added": $count }""")
          .build
      )

case class ParseException(error: String, content: String) extends Exception(error)

def parseStockPriceItems(json: String)(using lambdaLogger: LambdaLogger): Try[Iterable[StockPriceItem]] =
  lambdaLogger.log(json)
  Try(JsonNodeParser.create.parse(json).asArray.asScala.map(StockPriceItem.apply))

def putIntoDynamoDB(stockPriceItems: Iterable[StockPriceItem])(using lambdaLogger: LambdaLogger): Try[Long] = Try {
  val writeRequests = stockPriceItems.map {
    stockPriceItem =>
      val request = PutRequest.builder.item(stockPriceItem.dynamoDBAttributeMap).build
      WriteRequest.builder.putRequest(request).build
  }
  val requestItems = Map(tableName -> writeRequests.toList.asJava).asJava
  val batchWriteItemRequest = BatchWriteItemRequest.builder.requestItems(requestItems).build
  val batchWriteItemResponse = dynamoDbClient.batchWriteItem(batchWriteItemRequest)
  if (batchWriteItemResponse.hasUnprocessedItems && batchWriteItemResponse.unprocessedItems.size > 0) {
    val message = s"Wrote ${writeRequests.size - batchWriteItemResponse.unprocessedItems.size} of ${writeRequests.size}"
    throw new Exception(message)
  } else {
    lambdaLogger.log("Success")
    writeRequests.size
  }
}

def errorToResult(ex: Throwable)(using lambdaLogger: LambdaLogger): APIGatewayV2HTTPResponse =
  ex match
    case ParseException(error, content) =>
      val message = s"Error parsing request $error in $content"
      lambdaLogger.log(message)
      APIGatewayV2HTTPResponse.builder.withStatusCode(400).withBody(message).build
    case _ =>
      throw ex
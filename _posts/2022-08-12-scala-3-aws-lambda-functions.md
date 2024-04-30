---
title: "Scala 3 and AWS Lambda Functions"
categories:
  - DevOps/Platform
  - Scala
tags:
  - AWS
  - GitHub
  - Cloud/Serverless
excerpt_separator: <!--more-->
example: scala3-aws-lambda-dynamodb-importer
---

AWS Lambda offer the ability to run code functions without a server. Basically standalone functions that receive JSON as
a parameter and have up to 15 minutes to do anything. The source of the JSON event can be anything, AWS has configured
most of their AWS products to emit events; for example uploading a file to S3 creates JSON that contains information
about the file. Lambdas are meant to be simple and short-lived code snippets, so each Lambda can only listen to 1 source
for events (although you can proxy multiple types of events through a single source). The most generic source for events
is to listen to HTTP requests on a public URL, and we'll cover how that can be done in this article.<!--more-->

{% include table-of-contents.html height="400px" %}

That's it; and in this function you can do _anything_. The function has predefined CPU and RAM limits which are
configurable between 128MB and 10GB of RAM, with up to 10GB of ephemeral `/tmp` storage.

# Basics of AWS Lambda

Amazon AWS pioneered the FaaS (Functions as a Service) space in 2014, but Microsoft Azure and Google Cloud quickly
followed with their own products in 2016. An AWS Lambdas takes an `event` parameter and a `Context` that represents the
execution environment:

```scala
def handleRequest(event: Event, context: Context): Response
```

The source of the `event` will determine what it is, within AWS all events are JSON however AWS will automatically parse
the JSON using an internal Jackson library to any POJO specified in the function. For convenience AWS has made
a `aws-lambda-java-events` package that contains classes for all AWS events. Similarly, any class that can be
serialized by Jackson can be used as a `Response` output.

When AWS Lambdas are configured as public Internet endpoints they will be accessible at

```
https://<url-id>.lambda-url.<region>.on.aws
```

In this case, the most appropriate `Event` and `Response` classes to use are of the APIGateway:

```scala
import com.amazonaws.services.lambda.runtime.events.{APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse}

class Handler extends RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse] {
  override def handleRequest(event: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse
}
```

The `APIGatewayV2HTTPEvent` contains fields for standard HTTP information including headers, IP and the body as
a `String` when available. Fortunately binary POST data is supported via Base64 encoding keeping Lambdas flexible to all
use-cases. Streaming requests are also supported but beyond the scope of this introduction.

# AWS DynamoDB Use Case

Access to all AWS services is available to the Lambda using the AWS SDK. A simple use-case to study is writing POST data
to DynamoDB.
For sample data, consider the need to write price information about stocks at various times. Our DynamoDB `stock_prices`
table looks like:

```
Partition key: symbol (String)
Sort key: time (Number)
Attribute1: prices (Binary)
```

Consider a POST payload to our Lambda of:

```JSON
[
  {
    "symbol": "SPY",
    "time": 1660229200,
    "prices": "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxOS43OCwicHJldmlvdXNDbG9zZSI6NDIwLjAwfQ=="
  },
  {
    "symbol": "SPY",
    "time": 1660142800,
    "prices": "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxMi42NywicHJldmlvdXNDbG9zZSI6NDE5LjMzfQ=="
  },
  {
    "symbol": "SPY",
    "time": 1660056400,
    "prices": "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxOC4xMiwicHJldmlvdXNDbG9zZSI6NDE4Ljk4fQ=="
  }
]
```

The pseudocode would be:

```
request -> getBody -> parseItems(body) -> putN(item) -> response
```

## Parsing JSON

One of the breakages in Scala 3 due to macros being removed is that Jackson deserialization will not work. This means
that JSON parsing has to be explicit, but for only 3 fields this is quite simple.

```scala
case class StockPriceItem(symbol: String, time: String, prices: String) {
  def this(jsonNode: JsonNode) = this(
    jsonNode.field("symbol").get.asString,
    jsonNode.field("time").get.asNumber,
    jsonNode.field("prices").get.asString
  )
}
```

Parsing a `String` to down individual `JsonNode` is done using the AWS SDK's internal JSON library:

```scala
import software.amazon.awssdk.protocols.jsoncore.{JsonNode, JsonNodeParser}

def parseStockPriceItems(json: String): Try[Iterable[StockPriceItem]] = {
  Try(JsonNodeParser.create.parse(json).asArray.asScala.map(StockPriceItem.apply))
}

val items = Option(event.getBody).map(parseStockPriceItems)
```

## Interacting with DynamoDB

The first thing to consider is permissions. Permissions can be attached to the event, or we can use the permissions that
the Lambda is currently executing under. The currently executing credentials are available using
their `DefaultCredentialsProvider` in the AWS SDK.

```scala
val dynamoDbClient = DynamoDbClient.builder
  .credentialsProvider(DefaultCredentialsProvider.create)
  .region(Region.US_EAST_1)
  .build
```

While Lambda functions should be considered stateless, they are fror practicality reused if still in memory, called
hot-loading. A small performance optimization to avoid reinitializing of the credential provider can be to put it into
global/static scope.

The DynamoDB client writes data to the table is via a `Map` request to `putItem`:

```scala
val pricesByteArray = Base64.getDecoder.decode(prices)
val dynamoDBAttributeMap = Map(
  //writing String data
  "symbol" -> AttributeValue.builder.s(symbol).build,
  //writing Numberic data
  "time" -> AttributeValue.builder.n(time).build,
  //writing Binary data
  "prices" -> AttributeValue.builder.b(SdkBytes.fromByteArray(pricesByteArray)).build
)
val request = PutItemRequest.builder.tableName("stock_prices").item(dynamoDBAttributeMap).build
val putItemResponse = dynamoDbClient.putItem(request)
```

## Writing the response

The format and contents of the response depends on how the Lambda response is consumed. The easiest pattern is to either
return a `String` containing custom JSON (if not being consumed within AWS), or using the corresponding AWS SDK response
class that matches the event class.

Here, we will return a `APIGatewayV2HTTPResponse` to match the `APIGatewayV2HTTPEvent` input event.

The functional flow will use `Try` to handle both happy-path and exceptions:

```scala
def parseStockPriceItems(json: String): Try[Iterable[StockPriceItem]]
def putIntoDynamoDB(stockPriceItems: Iterable[StockPriceItem]): Try[Long]
def errorToResult(ex: Throwable): APIGatewayV2HTTPResponse

body
  .flatMap(parseStockPriceItems)
  .flatMap(putIntoDynamoDB)
  .fold(errorToResult, count =>
    APIGatewayV2HTTPResponse.builder
      .withStatusCode(200)
      .withBody(s"""{ "added": $count }""")
      .build
  )
```

## Errors

Handling errors always add complexity to code. AWS exposes logging to CloudWatch through their `context` object, as well
as SLF4J wrappers. The way Lambdas work is that any unhandled exception will result in a `502 BAD GATEWAY`. Non-200
errors can also be thrown if the output response can't be serialized to an expected output. In this example, by choice
we are choosing to only allow handled JSON parsing exceptions (caught and wrapped into a `ParseException`) be serialized
to a 200 response, and all unhandled exceptions to fail the Lambda (as a 502).

```scala3
given lambdaLogger: LambdaLogger = context.getLogger

case class ParseException(error: String, content: String) extends Exception(error)

def errorToResult(ex: Throwable)(using lambdaLogger: LambdaLogger): APIGatewayV2HTTPResponse =
  ex match
    case ParseException(error, content) =>
      val message = s"Error parsing request $error in $content"
      lambdaLogger.log(message)
      APIGatewayV2HTTPResponse.builder.withStatusCode(400).withBody(message).build
    case _ =>
      throw ex
```

# Automated Deployment from GitHub Actions

While AWS has their own internal CI/CD pipeline similiar to GitHub, but it is important to continue to view cloud
providers as commodity and interchangeable. GitHub (which is hosted in Azure) can easily interact with AWS.

The GitHub Action is a short snippet of YAML:

```yaml
- name: Build Assembly
  run: sbt test assembly
- name: AWS Update Lambda Action
  uses: stcalica/update-lambda@359ca7975ee5cc5c389fc84b0e11532e39f7b707
  with:
    package: "./target/scala-3.1.3/scala3-aws-lambda-dynamodb-importer-assembly-0.1.0.jar"
    function-name: "dynamo-import"
    AWS_SECRET_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    AWS_SECRET_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
    AWS_REGION: "us-east-1"
```

## AWS Permission Configuration for Lambdas

The CI/CD pipeline will automatically deploy to AWS, but the permissions and Lambda must be initially created.
TODO: See [Future Article Here]



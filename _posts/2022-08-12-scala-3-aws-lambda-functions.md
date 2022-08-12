---
title: "Scala 3 and AWS Lambda Functions"
categories:
  - Platform
  - Scala
tags:
  - AWS
  - Github
---
{% include postlogo.html title="Amazon Lambda" src="/assets/images/2022/08/Amazon_Lambda_architecture_logo.svg.png" %}
# What is AWS Lambda
AWS Lambda offer the ability to run code functions without a server.  Basically standalone functions that receive JSON as a parameter and have up to 15 minutes to do anything. The source of the JSON event can be anything, AWS has configured most of their AWS products to emit events; for example uploading a file to S3 creates JSON that contains information about the file. Lambdas are meant to be simple and shortlived code snippets, so each Lambda can only listen to 1 source for events (although you can proxy multiple types of events through a single source).  The most generic source for events is to listen to HTTP requests on a public URL, and we'll cover how that can be done in this article.

That's it.  And in this function you can do _anything_.  The function has predefined CPU and RAM limits which are configurable between 128MB and 10GB of RAM, with up to 10GB of ephemiral `/tmp` storage.

# Basics of AWS Lambda

Amazon AWS pioneered the FaaS (Functions as a Service) space in 2014, but Microsoft Azure and Google Cloud quickly followed with their own products in 2016.  An AWS Lambdas takes an `event` parameter and a `Context` that represents the execution environment:

```scala
def handleRequest(event: Event, context: Context): Response
```

The source of the `event` will determine what it is, within AWS all events are JSON however AWS will automatically parse the JSON using an internal Jackson library to any POJO specified in the function.  For convinience AWS has made a `aws-lambda-java-events` package that contains classes for all AWS events. Similarily, any class that can be serialized by Jackson can be used as a `Response` output.

When AWS Lambdas are configured as public Internet endpoints they will be accessible at `https://<url-id>.lambda-url.<region>.on.aws`.  In this case, the most appropriate `Event` and `Response` classes to use are of the APIGateway:

```scala
import com.amazonaws.services.lambda.runtime.events.{APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse}

class Handler extends RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse] :
  override def handleRequest(event: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse =
```

The `APIGatewayV2HTTPEvent` contains fields for standard HTTP information including headers, IP and the body as a `String` when available.  Fortunately binary POST data is supported via Base64 encoding keeping Lambdas flexible to all usecases.  Streaming requests are also supported but beyond the scope of this introduction.

# AWS DynamoDB Usecase

Access to all AWS services is available to the Lambda using the AWS SDK.  A simple use-case to study is writing POST data to DynamoDB.
For sample data, consider the need to write price information about stocks at various times. Our DynamoDB `stock_prices` table looks like:
```
Partition key: symbol (String)
Sort key: time (Number)
Attribute1: prices (Binary)
```

Consider a POST payload to our Lambda of:
```JSON
[
  { "symbol": "SPY", "time": 1660229200, "prices": "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxOS43OCwicHJldmlvdXNDbG9zZSI6NDIwLjAwfQ==" },
  { "symbol": "SPY", "time": 1660142800, "prices": "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxMi42NywicHJldmlvdXNDbG9zZSI6NDE5LjMzfQ==" },
  { "symbol": "SPY", "time": 1660056400, "prices": "eyJyZWd1bGFyTWFya2V0UHJpY2UiOjQxOC4xMiwicHJldmlvdXNDbG9zZSI6NDE4Ljk4fQ==" }  
]
```
The pseudocode would be: 
```
request -> getBody -> parseItems(body) -> putN(item) -> response
```

## Parsing JSON

One of the breakages in Scala 3 due to macros being removed is that Jackson deserialization will not work.  This means that JSON parsing has to be explicit, but for only 3 fields this is quite simple.
```scala
case class StockPriceItem(symbol: String, time: String, prices: String):
  def this(jsonNode: JsonNode) = this(
    jsonNode.field("symbol").get.asString,
    jsonNode.field("time").get.asNumber,
    jsonNode.field("prices").get.asString
  )
```
Parsing a `String` to down individual `JsonNode` is done using the AWS SDK's internal JSON library:
```scala
import software.amazon.awssdk.protocols.jsoncore.{JsonNode, JsonNodeParser}

val items = Option(event.getBody).withFilter(!_.isBlank).map {
  json => 
    JsonNodeParser.create.parse(json).asArray.asScala.map(StockPriceItem.apply)
}
```


## Interacting with DynamoDB

The first thing to consider is permissions.  Permissions can be attached to the event, or we can use the permissions that the Lambda is currently executing under.  The currently executing credentials are available using their `DefaultCredentialsProvider` in the AWS SDK.

```scala
val dynamoDbClient = DynamoDbClient.builder
  .credentialsProvider(DefaultCredentialsProvider.create)
  .region(Region.US_EAST_1)
  .build
```

While Lambda functions should be considered stateless, they are fror practicality reused if still in memory, called hot-loading. A small performance optimization to avoid reinitializing of the credential provider can be to put it into global/static scope.

The DynamoDB client writes data to the table is via a `Map` request to `putItem`:
```
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





# Scala and Other Languages

The 2 main factors to consider when implementing Lambda functions are:
- execution time per call
- number of calls.

While there is discussion about first-call latency (https://mikhail.io/serverless/coldstarts/aws/languages/) it tends to affect only a small number of usecases.  AWS will keep most lambda code hot-loaded for hours so which the shock of even comparing a 20MB Java JAR to 50 lines of Python code boils down to nothing.  There are optimizations that can be had both in aggregate resource cost of execution between using Python versus the JVM it would easily be outweighed by initial engineering costs by forcing developers to work outside their language of expertise.

According to https://www.datadoghq.com/state-of-serverless/ Python is the most popular language for Lambda, with NodeJS being a close second.  This aligns with the lightweight market that Lambdas excel at. However Datadog also indicates that over 60% of large organizations have deployed Lambda in 3 or more languages meaning that they are reaching into more stuctured languages such as Java, Go, or .Net for other, more likely complex, tasks.

# Common Action: Interacting with an AWS Service

## Inserting items into AWS DynamoDB

### AWS permissions

Pure computational request use-cases are rare and there are better AWS services available for proxying of requests, the most Lambda functions will have 1 or more interactions with another AWS service.  Thankfully the AWS SDK has streamlined this process.  



# Automated deployment from Github Actions

## Artifact Sizes of Java versus Scala

Maintaining lightweight resource usage is the key to keeping execution costs low.  Unfortunately the overhead of the JVM already places it behind Python and NodeJS deployments, but less than a full containerized build. Library dependencies should be kept to the minimum since JVM artifacts do not perform tree-shaking code removal that Go or GraalVM will.

| Size | Name                        |                                                  |
|------|-----------------------------|--------------------------------------------------|
|6.9 MB|aws-lambda-java-core         |Mandatory                                         |
|2.2 MB|aws-lambda-java-serialization|Optional to support custom POJOs                  |
|9.9 MB|awssdk-[1st]-service         |Mandatory for interacting with other AWS services |
|2.0 MB|awssdk-[additional]-service  |For each additional AWS service after the first   |
|5.7 MB|Scala 2.13                   |Mandatory for Scala 2/3                           |
|1.2 MB|Scala 3.1                    |Mandatory only for Scala 3                        |

Note that AWS Lambda Layers allows shared `/lib` folder however all dependencies continue to contribute to runtime resource usage. Mandatory libraries make it unlikely to be able to run any JVM Lambda with the minimal 128MB RAM, typically the requiring at least 150MB.

While the AWS SDK represents 9.9MB above, the majority is contributed by shared libraries rather than code specific to the DynamoDB service.  Additional services can be added with minimal size increase, for example adding the `awssdk-s3` to support read/write from S3 would be 3 MB, or `awsdsk-sns` to support Notifications would be 1 MB.


## CI/CD

Of course AWS has their own internal CI/CD pipeline similiar to GitHub, but it is important to continue to view cloud providers as commodity and interchangeable.  Github (which is hosted in Azure) can easily interact with AWS.

The Github Action is a short snippet of YAML:
```yaml
- name: Build Assembly
  run: sbt assembly
- name: AWS Update Lambda Action
  uses: stcalica/update-lambda@359ca7975ee5cc5c389fc84b0e11532e39f7b707
  with:
    package: "./target/scala-3.1.3/aws-lambda-dynamo-import-assembly-0.1.0.jar"
    function-name: "dynamo-import"
    AWS_SECRET_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    AWS_SECRET_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
    AWS_REGION: "us-east-1"
```


Describes how to get setup a function Url Lambda Function and CI/CD pipeline from Github

Deployed as a single assembly JAR, or using AWS Lambda Layers to have shared `/lib`.
Standard Scala plugins, 


{% 
  include github_project.html 
  name="AWS Lambda DynamoDB importer"
  url="https://github.com/stevenrskelton/scala3-aws-lambda-dynamodb-importer"
  description="Lambda function Url that inserts into a DynamoDB table"
%}
---
title: "Scala 3 and AWS Lambda Functions"
categories:
  - Platform
  - Scala
tags:
  - AWS
  - Github
published: false
---
# What is AWS Lambda
AWS Lambda offer the ability to run code functions without a server.  Basically, JSON events are passed to the Lambda, and AWS allows the block of code up to 15 minutes to do anything.  The source for events can be anything, AWS has configured all of their AWS products to emit events - for example uploading a file to S3 creates a JSON blob that contains some information about the file.  Each Lambda can only listen to 1 source of events, and a very generic source would be to have Lambda listen to HTTP events at a specific URL, and the JSON would be the body of the request.

All Lambda functions take a JSON event parameter and a `Context` that represents the execution environment:

```scala
def handleRequest(event: JsonObject, context: Context): String
```
AWS Lambda is smart enough to use the Jackson JSON library to parse the event, so the `JsonObject` can be parsed to any POJO you would expect.  In this example, lets define an arbitray data class (in Scala 3):
```scala
class StockPriceItem:
  @JsonProperty("stockId") var stockId: String = ""
  @JsonProperty("tradingDay") var tradingDay: String = ""
  @JsonProperty("proto") var proto: String = ""
```
Then the signature for the Lambda function would be:
```scala
class Handler extends RequestHandler[java.util.List[StockPriceItem], String] :
```
That's it.  And in this function you can do _anything_.  The function has predefined CPU and RAM limits, but they can be set between 128MB and 10GB of RAM, with up to 10GB of ephemiral `/tmp` storage.

# Scala 3
One of the con

https://acloudguru.com/blog/engineering/serverless-showdown-aws-lambda-vs-azure-functions-vs-google-cloud-functions

https://mikhail.io/serverless/coldstarts/aws/languages/

https://www.datadoghq.com/state-of-serverless/


# Common Action: Interacting with an AWS Service
## Inserting items into AWS DynamoDB

## AWS permissions

Setting this up as a global variable so that is can be reused between concurrent requests and while hot-loaded.
```scala
val dynamoDbClient = DynamoDbClient.builder
  .credentialsProvider(DefaultCredentialsProvider.create)
  .region(Region.US_EAST_1)
  .build
```


# Automated deployment from Github Actions


Describes how to get setup a function Url Lambda Function and CI/CD pipeline from Github

Deployed as a single assembly JAR, or using AWS Lambda Layers to have shared `/lib`.
Standard Scala plugins, 
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


{% 
  include github_project.html 
  name="AWS Lambda DynamoDB importer"
  url="https://github.com/stevenrskelton/aws-lambda-dynamo-import"
  description="Lambda function that inserts new items into a DynamoDB table"
%}

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
AWS Lambda offer the ability to run functions of code on demand.  Generally this looks like a JSON event parameter, along with a `Context` that contains 
properties of the Lambda's execution environment:

```scala
def handleRequest(event: JsonObject, context: Context): String
```

Depending on the event trigger, 
The `JsonObject` here depends on the event trigger.  Lambda functions can be triggered automatically by things happening within other AWS services.  An e, 
such as a file being uploaded to S3, or an alert from AWS CloudWatch.  


Describes how to get setup a function Url Lambda Function and CI/CD pipeline from Github



{% 
  include github_project.html 
  name="AWS Lambda DynamoDB importer"
  url="https://github.com/stevenrskelton/aws-lambda-dynamo-import"
  description="Lambda function that inserts new items into a DynamoDB table"
%}

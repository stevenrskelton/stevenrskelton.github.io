# scala3-aws-lambda-dynamodb-importer

AWS Lambda function written in both Scala3 and Python which handles URL POST requests to insert items into a DynamoDB table.

Github Action to automatically push new builds to AWS as part of a CI/CD pipeline.

[![Publish to AWS](https://github.com/stevenrskelton/scala3-aws-lambda-dynamodb-importer/actions/workflows/publish-to-aws.yml/badge.svg)](https://github.com/stevenrskelton/scala3-aws-lambda-dynamodb-importer/actions/workflows/publish-to-aws.yml)

## AWS Lambda with interaction to other AWS services and CI/CD
### https://www.stevenskelton.ca/scala-3-aws-lambda-functions/

Features:
- Read JSON from HTTP body
- Batch Put to DynamoDB
- JSON response containing put count
- Descriptive exception for missing body
- Descriptive exception for missing fields
- AWS CloudWatch logging for unhandled exceptions


## JVM versus Python
### https://www.stevenskelton.ca/jvm-versus-python-aws-lambda-functions/
Python implementation is at [/src/main/python/handler.py](/src/main/python/handler.py)

|                           | Scala / JVM | Python    |
|---------------------------|------------:|----------:|
| Lines of Code             | 86          | 61        |
| File size                 | 17.6 MB     | 1.7 KB    |
| Cold Start                |             |           |
| - Init duration           | 429.39 ms   | 315.41 ms |
| - Duration                | 11077.39 ms | 274.72 ms |
| - Max memory used         | 152 MB      | 67 MB     |
| Hot Load                  |             |           |
| - Duration                | 21.48 ms    | 13.97 ms  |
| - Max memory used         | 153 MB      | 70 MB     |












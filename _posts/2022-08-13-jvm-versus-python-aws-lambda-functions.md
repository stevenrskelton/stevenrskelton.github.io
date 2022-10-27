---
title: "JVM versus Python for AWS Lambda Functions"
categories:
  - Scala
tags:
  - AWS
  - Serverless
---
The suitability of programming languages across different domains is a contested topic.  AWS Lambda Functions are a serverless solution that can be used for a wide range of problems from tiny to large tasks.  For lightweight tasks how does the JVM stack up?

## Lambda Functions in Scala versus Other Languages

The most widely used programming language for Lambda Functions is Python.  Benchmarks show Python offers the best performances, and the language simplicity normally results in faster development and less code for lightweight tasks.  But AWS Lambda can offer massive scale with access to up to 10GB RAM and 15 minutes per execution that are typically benefited from the structure and maintainability offered in languages such as Java, Scala, and C#.  This article investigates how the JVM stacks up on the low-end and if languages like Python are the only choice.

## Sample App

Features:
- Usability: Read JSON from HTTP body, JSON response
- Functionality: Interact with another AWS service (Batch Put to DynamoDB)
- Error Handling: Descriptive exception for missing body, fields
- Logging: To AWS CloudWatch logging for unhandled exceptions

See the full article about the Scala 3 implementation at
[Scala 3 and AWS Lambda Functions]({% post_url 2022-08-12-scala-3-aws-lambda-functions %}).

The Python 3.9 implementation is available on GitHub at

[https://github.com/stevenrskelton/scala3-aws-lambda-dynamodb-importer/blob/main/src/main/python/handler.py](https://github.com/stevenrskelton/scala3-aws-lambda-dynamodb-importer/blob/main/src/main/python/handler.py)

### JVM versus Python Performance Comparison

While [there is discussion](https://mikhail.io/serverless/coldstarts/aws/languages/) about first-call latency it tends to affect only a small number of usecases.  AWS will keep most lambda code hot-loaded for hours so which the shock of even comparing a 20MB Java JAR to 60 lines of Python code boils down to nothing.  There are optimizations that can be had both in aggregate resource cost of execution between using Python versus the JVM it would easily be outweighed by initial engineering costs by forcing developers to work outside their language of expertise.

There are features such as AWS Lambda Layers that allow for a shared library [it is reported](https://www.simform.com/blog/lambda-cold-starts/) that they have only sub-50ms improvement to cold-starts.  It appears the there is no way to optimize JVM overhead away, only minimize the burden by reducing overall dependencies.

<table style="margin-left:auto;margin-right:auto;width:500pt;display:table;">
  <thead>
    <tr>    
      <th></th>
      <th style="text-align:center">Scala / JVM</th>
      <th style="text-align:center">Python</th>  
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="font-weight:bold;padding-left:20pt">Lines of Code</td>
      <td style="text-align:right;padding-right:20pt">86</td>
      <td style="text-align:right;padding-right:20pt">61</td>
    </tr>
    <tr>
      <td style="font-weight:bold;padding-left:20pt">File size</td>
      <td style="text-align:right;padding-right:20pt">17.6 MB</td>
      <td style="text-align:right;padding-right:20pt">1.7 KB</td>
    </tr>
    <tr>
      <td style="font-weight:bold;padding-left:20pt">Cold Start</td>
      <td></td>
      <td></td>
    </tr>
    <tr>
      <td style="padding-left:30pt">• Init duration</td>
      <td style="text-align:right;padding-right:20pt">429.39 ms</td>
      <td style="text-align:right;padding-right:20pt">315.41 ms</td>
    </tr>
    <tr>
      <td style="padding-left:30pt">• Duration</td>
      <td style="text-align:right;padding-right:20pt">11077.39 ms</td>
      <td style="text-align:right;padding-right:20pt">274.72 ms</td>
    </tr>
    <tr>
      <td style="padding-left:30pt">• Max memory used</td>
      <td style="text-align:right;padding-right:20pt">152 MB</td>
      <td style="text-align:right;padding-right:20pt">67 MB</td>
    </tr>
    <tr>
      <td style="font-weight:bold;padding-left:20pt">Hot Load</td>
      <td></td>
      <td></td>
    </tr>
    <tr>
      <td style="padding-left:30pt">• Duration</td>
      <td style="text-align:right;padding-right:20pt">21.48 ms</td>
      <td style="text-align:right;padding-right:20pt">13.97 ms</td>
    </tr>
    <tr>
      <td style="padding-left:30pt">• Max memory used</td>
      <td style="text-align:right;padding-right:20pt">153 MB</td>
      <td style="text-align:right;padding-right:20pt">70 MB</td>
    </tr>
  </tbody>
</table>

### Minimizing JVM Artifact Size

Maintaining lightweight resource usage is the key to keeping execution costs low.  Unfortunately the overhead of the JVM already places it behind Python and NodeJS deployments, but less than a full containerized build. Library dependencies should be kept to the minimum since JVM artifacts do not perform tree-shaking code removal that Go or GraalVM will.

<table style="margin-left:auto;margin-right:auto;width:700pt;display:table;">
  <thead>
    <tr>    
      <th style="text-align:center">Size</th>
      <th style="text-align:center">Artifact Name</th>
      <th style="text-align:center">Use</th>  
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="text-align:right">6.9 MB</td>
      <td>aws-lambda-java-core</td>
      <td>Mandatory</td>
    </tr>
    <tr>
      <td style="text-align:right">0.4 MB</td>
      <td>aws-lambda-java-events</td>
      <td>Optional to support AWS event POJOs</td>
    </tr>
    <tr>
      <td style="text-align:right">2.2 MB</td>
      <td>aws-lambda-java-serialization</td>
      <td>Optional to support custom POJO serialization</td>
    </tr>
    <tr>
      <td style="text-align:right">9.9 MB</td>
      <td>awssdk-[1st]-service</td>
      <td>Mandatory for interacting with other AWS services</td>
    </tr>
    <tr>
      <td style="text-align:right">2.0 MB</td>
      <td>awssdk-[additional]-service</td>
      <td>For each additional AWS service after the first</td>
    </tr>
    <tr>
      <td style="text-align:right">5.7 MB</td>
      <td>Scala 2.13</td>
      <td>Mandatory for Scala 2/3</td>
    </tr>
    <tr>
      <td style="text-align:right">1.2 MB</td>
      <td>Scala 3.1</td>
      <td>Mandatory only for Scala 3</td>
    </tr>
  </tbody>
</table>

While the AWS SDK represents 9.9MB above, the majority is contributed by shared libraries rather than code specific to the DynamoDB service.  Additional services can be added with minimal size increase, for example adding the `awssdk-s3` to support read/write from S3 would be 3 MB, or `awsdsk-sns` to support Notifications would be 1 MB.

## Conclusion

According to cloud monitoring SaaS Datadog [Python is the most popular language for Lambda](https://www.datadoghq.com/state-of-serverless/) with NodeJS being a close second.  This aligns with the lightweight market that Lambdas excel at. However Datadog also indicates that over 60% of large organizations have deployed Lambda in 3 or more languages meaning that they are reaching into more stuctured languages such as Java, Go, or .Net for other, more likely complex, tasks.


{% 
  include github_project.html 
  name="AWS Lambda DynamoDB importer"
  url="https://github.com/stevenrskelton/scala3-aws-lambda-dynamodb-importer"
  description="See the Complete Code on GitHub"
%}

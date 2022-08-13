---
title: "Scala 3 verus Python with AWS Lambda Functions"
categories:
  - Scala
tags:
  - AWS
published: false
---
# Lambda in Scala versus Other Languages

The 2 main factors to consider when implementing Lambda functions are:
- execution time per call
- number of calls.

While there is discussion about first-call latency (https://mikhail.io/serverless/coldstarts/aws/languages/) it tends to affect only a small number of usecases.  AWS will keep most lambda code hot-loaded for hours so which the shock of even comparing a 20MB Java JAR to 50 lines of Python code boils down to nothing.  There are optimizations that can be had both in aggregate resource cost of execution between using Python versus the JVM it would easily be outweighed by initial engineering costs by forcing developers to work outside their language of expertise.

According to https://www.datadoghq.com/state-of-serverless/ Python is the most popular language for Lambda, with NodeJS being a close second.  This aligns with the lightweight market that Lambdas excel at. However Datadog also indicates that over 60% of large organizations have deployed Lambda in 3 or more languages meaning that they are reaching into more stuctured languages such as Java, Go, or .Net for other, more likely complex, tasks.

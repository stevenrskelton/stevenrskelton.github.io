---
published: false
title: "Migration from Akka Futures to ZIO"
categories:
  - Scala
tags:
  - Akka
  - ZIO
  - TradeAudit
---

Akka / [Apache Pekko](https://pekko.apache.org/) is a robust, popular, and thoroughly tested Scala framework. One of the concurrency primatives it uses is the standard `scala.concurrent.Future` class.  Before these existed in Scala, there was the [Twitter `Future`](https://twitter.github.io/util/guide/util-cookbook/futures.html) offering similiar, but expanded functionality including cancellation/interuptability.  Ignoring the functional coding style promoted by [ZIO](https://zio.dev/) for a second, the concurrency primative used by ZIO, known as `ZIO[R, E, A]` can be viewed as a more advanced `Future[A]`.


- cancellability / interruption backpressure

and  is however  The BSL License wouldn't apply to [TradeAudit](https://tradeaudit.app), the Scala eco-system is migrating dependencies to the  project based off of the last unrestricted Akka release.  
Scala 3 deprecated its macros, to be replaced with a ground-up new implementation.  While Scala 3 projects easily run Scala 2 compiled code, they are unable to execute any Scala 2 macros meaning many Scala 2 libraries built around macros could not be used.  Some were rewritten for the new macros, such as [Play JSON](https://github.com/playframework/play-json), but others such as    (in fact the standard Collections library This was the primary blocker for  style of macros, is a worthy reason to introduce breaking changes to the Scala ecosystem.  While care was taken to make the transition as easy as possible, 

| Functionality       | Original                                               | Target                                                       | 
|:--------------------|:-------------------------------------------------------|:-------------------------------------------------------------|
| Scala               | 2.13                                                   | 3.3                                                          |
| HTTP server         | akka-http                                              | zio-http                                                     |
| HTTP client         | • play-ahc-ws-standalone<br/>• play-ws-standalone-json | zio-http                                                     |
| gRPC server         | • akka-http <br/>• (JVM TLS)                           | • grpc-netty<br/>• scalapb-runtime-grpc<br/>• netty-tcnative |
| gRPC generation     | sbt-akka-grpc                                          | • sbt-protoc<br/>• zio-grpc-codegen                          |
| JSON                | play-json                                              | (zio-json)?                                                  |
| FTP client          | akka-stream-alpakka-ftp                                | zio-ftp                                                      |
| SQL                 | akka-stream-alpakka-slick                              | quill-jdbc-zio                                               |
| Workflows           | akka-stream                                            | • pekko-stream<br/>• zio                                     |
| Configuration Files | typesafe config                                        | +(zio-config-typesafe)?                                      |
| Cache               | scaffeine                                              | zio-cache                                                    |
| Logging             | logback-classic                                        | +(zio-logging-slf4j2)?                                       |
| Test                | • scalatest<br/>• scalamock                            | ?                                                            |


The [bloc library](https://bloclibrary.dev/) is a predictable state management library for Dart.  allows for state management  a state managent tool 


Slick -> Quill

Scala 2 -> Scala 3

Remove ScalaMock

akka-stream-alpakka-ftp -> ZIO ftp

Akka gRPC -> ZIO gRPC
- rewrite HTTP routes
- move authenticated user into function parameters rather than class level

Akka HTTP -> ZIO HTTP
play-ahc-ws-standalone
play-ws-standalone-json

logger -> ZIO.logger

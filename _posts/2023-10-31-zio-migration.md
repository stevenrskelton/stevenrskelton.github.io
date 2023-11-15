---
title: "ZIO migration from Akka and Futures"
categories:
  - Scala
tags:
  - Akka-Pekko
  - ZIO
  - TradeAudit
---

Akka / [Apache Pekko](https://pekko.apache.org/) is a robust and popular Scala framework used to build concurrent production-grade software. One of the concurrency primatives it uses is the standard `scala.concurrent.Future` class.  Before these existed in Scala, there was the [Twitter `Future`](https://twitter.github.io/util/guide/util-cookbook/futures.html) offering similiar, but expanded functionality including cancellation/interuptability.  Ignoring the functional coding style promoted by [ZIO](https://zio.dev/) for a second, the concurrency primative used by ZIO, known as `ZIO[R, E, A]` can be viewed as a more advanced `Future[A]`.

{% include toc %}

One notices `ZIO` has 2 additional type parameters; each of these offer functionality `Future` is incapable of.

## ZIO Environment R

The ZIO environment allows dependencies to be defined that are required to run the `ZIO`.  For example, if there were 2 dependencies to run a zio, `Service1` and `Service2`, the zio type would be `zio : ZIO[Service1 | Service2, _, _]`.  This cannot be run until we provide them, using `zio.provide(service1, service2)`.

By including the dependencies as a type, it offers flexibility not offered by other compile-time DI frameworks.

## ZIO Failure E

Java has pretty much abandoned [checked exceptions](https://www.baeldung.com/java-checked-unchecked-exceptions) because of a poor implementation, but the idea is sound.  It is often helpful to track what, if any, exceptions can be thrown. This can be as open as all classes including non-throwables `ZIO[_, Any, _]`, narrowed to exceptions (`ZIO[_, Exception, _]`, or as strict as not being able to throw exceptions `ZIO[_, Nothing, _]` without termination.

## Optionality of ZIO Environment and Failure Types

The ZIO type alias allow convenient opt-out of defining Environment or Failures.
```
type IO[+E, +A] = ZIO[Any, E, A]         // Succeed with an `A`, may fail with `E`
type Task[+A]   = ZIO[Any, Throwable, A] // Succeed with an `A`, may fail with `Throwable`
```
The `Task[A]` class can be viewed as a `Future[A]`.

# How to Partially Migrate: ZIO and Future interoptability

Being able to run `Future` and `ZIO` in the same project is straight-forward via conversion; similiar to how Java `CompletableFuture`, Twitter `Future`, and Scala `Future` provide efficient transformations.

To convert a `z: ZIO[Any, Any, A]` to a Future:
```
val f: Future[A] = Unsafe.unsafe {
  implicit u => zio.Runtime.default.unsafe.runToFuture(z).future
}
```
And to convert back:
```
val t: Task[A] = ZIO.fromFuture {
      implicit ec: ExecutionContext => f
}
```
Because `Future[A]` / `Task[A]` have no "checked" exceptions and indicate they can throw any `Exception` type, when migrating to use `ZIO` / `IO` types with checked exceptions any conversions from `Future` will need to provide restrictions either cast, wrap, or die in a `catchAll` handler:
```
class KnownException(message: String, cause: Throwable) extends Exception(message, cause)

val z: IO[KnownException, A] = t.catchAll {
  case ex: KnownException => ZIO.fail(ex)
  case ex => ZIO.fail(KnownException(ex.getMessage, ex)
} 
```

# Migration

ZIO has [documented the pathway from Akka to ZIO](https://zio.dev/guides/migrate/from-akka/), and it aligns with my personal experience migrating [Trade Audit](https://tradeaudit.app).

This code-base wasn't using any of the Cluster or distributed node functionality, it was a very straight-forward gRPC request handler and scheduled task executor with very limited data sharing between parallel tasks.

<table>
    <thead>
        <th>Order</th>
        <th>Functionality</th>
        <th>Original</th>
        <th>Target</th>
    </thead>
    <tbody>
        <tr>
            <td style="text-align:center">1</td><td>SQL</td><td>akka-stream-alpakka-slick</td><td>quill-jdbc-zio</td>
        </tr>
        <tr>
            <td rowspan="2" style="text-align:center">2</td><td>gRPC server</td><td>• akka-http <br/>• (JVM TLS)</td><td>• grpc-netty<br/>• scalapb-runtime-grpc<br/>• netty-tcnative</td>
        </tr>
        <tr>
            <td>gRPC generation</td><td>sbt-akka-grpc</td><td>• sbt-protoc<br/>• zio-grpc-codegen</td>
        </tr>
        <tr>
            <td style="text-align:center">3</td><td>HTTP server</td><td>akka-http</td><td>zio-http</td>
        </tr>
        <tr>
            <td rowspan="4" style="text-align:center">4</td><td>Scala</td><td>2.13</td><td>3.3</td>
        </tr>
        <tr>
            <td>Test</td><td>• scalatest<br/>• scalamock</td><td>?</td>
        </tr>
        <tr>
            <td>FTP client</td><td>akka-stream-alpakka-ftp</td><td>zio-ftp</td>
        </tr>
        <tr>
            <td>Workflows</td><td>akka-stream</td><td>• pekko-stream<br/>• zio</td>
        </tr>
        <tr>
            <td rowspan="3" style="text-align:center">option</td><td>HTTP client</td><td>• play-ahc-ws-standalone<br/>• play-ws-standalone-json</td><td>zio-http</td>
        </tr>
        <tr>
            <td>Configuration Files</td><td>typesafe config</td><td>+(zio-config-typesafe)?</td>
        </tr>
        <tr>
            <td>Cache</td><td>scaffeine</td><td>zio-cache</td>
        </tr>
        <tr>
            <td rowspan="2" style="text-align:center">n/a</td><td>JSON</td><td>play-json</td><td>(zio-json)?</td>
        </tr>
        <tr>
            <td>Logging</td><td>logback-classic</td><td>+(zio-logging-slf4j2)?</td>
        </tr>
    </tbody>
</table>

The order is broken down into steps: _1_, _2_, _3_, and _4_; there are _option_ migrations that might be done but for now they are using ZIO ↔ Future conversions.  Finally there are _n/a_ migrations that need to be evaluated if they provide any benefit.

## Step 1: Slick SQL to Quill

Slick and Quill are both based around constructing SQL syntax trees using lifted parameters.  Because they both model SQL rather than inventing their own DSL or abstraction, the migration from Slick queries to Quill required very little syntax change.  The key difference between Slick and Quill is when the SQL is generated:
- Slick SQL is generated during runtime (including their `Compiled` queries which cache the first runtime generation result)
- Quill SQL is generated at compiled type (unless the AST cannot be computed by the compiler)

A specific code format has to be programmed to allow compiler construction of the AST; this is slightly more rigorous in Scala 3, but never onerous.  A key concept to remember is that compiler generated SQL is faster because there is no runtime processing required, but because there is no runtime processing there is no variable output.  For every DB execute call there is only 1 possible query (varied on SQL parameters).  This can result in a large SQL statement (that is optimized away by the SQL engine), but it is possible to fan out queries to multiple execute calls within if/else branches.

### Quill ZIO

Quill doesn't require ZIO, it supports 4 installs: 
- with ZIO,
- with Cats/Monix (these are similiar to ZIO),
- blocking JDBC without ZIO,
- async JDBC without ZIO (legacy).

Async Futures JDBC is a highly efficient and performant solution for users not wanting to use fully functional / ZIO systems.

### Effect on Compile Time

Quill trades off compile time for greater runtime performance.  The sample project has 80 queries;

| Implementation | Scala 2 Compile Time | Scala 3 Compile Time |
|:---------------|:--------------------:|:--------------------:|
| Slick          |         45s          |         n/a          |
| Quill          |        4m 17s        |        8m 42s        |
| Quill Dynamic  |         n/a          |        7m 52s        |

8-core 4200MHz, 16GB RAM
Quill required > 4GB Heap to compile Scala 3


## Step 2: gRPC Migration from Akka to ZIO

## Step 3: Akka HTTP Server to ZIO HTTP Server

## Step 4: Scala 2 to 3

The Scala 3 was a full rewrite based on Dotty, and is less mature than the Scala 2 compiler.  It is noticably slower, however over time this is expected to improve.  The slowness of Quill is notable, outlined in [Step 1:Effect on Compile Time](#effect-on-compile-time).  This is an understandable reason to use Slick over Quill until code can be refactored to maximize incremental compilation, or avoid SQL compilation by moving queries to their own subproject artifact.

### Quill Query AST in Scala 3

The Scala 3 compiler rewrite also removed all support for Scala 2 macros.  Macros operate in a very different way, and this requires a more precise code structure to allow full visibility into the AST of SQL queries. The primary change required is to inline all steps of the query generation, this is achieved using the Scala 3 keyword `inline`.
```
val query = quote { ... }
```
to 
```
inline def query = ...
```
There are limitations to inlining, for example inline def cannot be nested.  Swaping `inline def` for `val` was a minor change, restructuing code to single level def depth was a more significant refactor.  Until this was complete those queries used the dynamic runtime generation fallback.


### Akka to Pekko

Another required change was enforcing that all Akka libraries had either been removed or migrated to Pekko.  The Akka BUSL 1.1 change took effect before all Akka libraries had Scala 3 compatible versions available; there is no Scala 3 compatible version of akka-http or akka-grpc under an Apache 2.0 license.


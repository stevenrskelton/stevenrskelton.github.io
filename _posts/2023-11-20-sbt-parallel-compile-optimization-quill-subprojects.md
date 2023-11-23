---
title: "SBT Parallel Compile Optimizations using Quill Sub-Projects"
categories:
  - Scala
tags:
  - SBT
  - GitHub
excerpt_separator: <!--more-->
---

{% include postlogo.html
title="XKCD"
src="/assets/images/2023/11/compiling.png"
url="https://xkcd.com/303"
caption="Scala is slow to compile."
%}

Advanced syntax constructs and a robust type system can increase developer productivity and runtime reliability but also
create extra work for the compiler. Macro libraries such as [Quill](https://zio.dev/zio-quill/) are effectivily programs
written for the compiler, and can represent an unbounded amount of work depending on what they are trying to accomplish.
Are there ways to
structure our Scala 3 code to ensure that we can embrace the rich macro ecosystem without excessively long compile
times?
<!--more-->

{% include table-of-contents.html height="600px" %}

# Symptoms of a Slow Compile

As Scala 2 matured compiler optimizations brought compilation times down, but the Scala 3 rewrite to TASTy reset many
optimizations back to zero. This article is an analysis of the steps used to optimize a Scala 3 project making heavy
use of macros via Quill.

[Quill](https://zio.dev/zio-quill/) is a macro library that adds compile-time query generation to the JVM. It is
similiar to other type-safe SQL generators such as [Slick](https://scala-slick.org/doc/3.0.0/index.html) in how SQL is
constructed using abstracted functions, but different in that the SQL is generated in the compile phase rather than
dynamically during runtime. So while the compile times may be slower in Quill, it eliminates a significant class of
runtime errors. This is a trade-off between compilation speed and compile-time functionality.

The example project is outlined in the previous post
[ZIO Migration from Akka and Scala Futures]({% post_url 2023-10-31-zio-migration %}), and consists of over 120 SQL query
macros of varying complexity, with some larger queries requiring over 5 minutes to complete and over +5GB of heap.

## Is The Scala 3 Compiler Optimized?

Simple answer, no - but that's a good thing: it will get better as Scala 3 matures. The switch from Scala 2 to Scala 3,
for the same functionality, represented twice as long compile times:

| Implementation | Scala 2 | Scala 3 |
|:---------------|:-------:|:-------:|
| Slick          |   45s   |   n/a   |
| Quill          | 4m 17s  | 8m 42s  |

While Scala 2 macros aren't apples-to-apples with Scala 3, the end functionality in Quill is the same a difference this
large wouldn't exist if Scala 3 was optimized.

## Problems Encountered

### Increased Heap Space Requirements

Compilation times can be affected by the amount of RAM available. Scala 3 Quill macros were observed to require
significantly more memory, even failing to compile with the JVM defaults.

```
[error] -- Error: /home/runner/work/tradeauditserver/src/main/scala/SQLWeb.scala:46:16 
[error] 46 |      quill.run(q).map {
[error]    |                ^
[error]    |                Exception occurred while executing macro expansion.
[error]    |                java.lang.OutOfMemoryError: Java heap space
[error]    |
[error]    |----------------------------------------------------------------------------
[error]    |Inline stack trace
[error]    |- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
[error]    |This location contains code that was inlined from Context.scala:80
```

Struggling garbage collection can have non-fatal effects, the compiler will warn when the amount of memory available
is an issue.

```
[warn] In the last 10 seconds, 6.884 (70.9%) were spent in GC. [Heap: 1.84GB free of 3.34GB, max 5.33GB] 
Consider increasing the JVM heap using `-Xmx` or try a different collector, e.g. `-XX:+UseG1GC`, for better performance.
```

### Macros Blocking Threads Preventing Multithreading

Long running macros (taking over 1 minute) were observed to block compiler from running other threads.

![Single Task CPU](/assets/images/2023/11/single_task_cpu.png)

This wasn't observed to be an issue in all projects, it appeared to be specific to macros being forced to run
sequentially within the same project. For SQL heavy projects, non-macro code is quickly compiled, resulting in idle
cores. Ideally, the entire compile task should be multithreaded from start to finish.

![Parallel Task CPU](/assets/images/2023/11/parallel_task_cpu.png)

# Code Refactoring and Configuration Changes

## Increased Heap Space

### Optimizing for the Build Server Hardware

Whether compiling locally or in a remote CI/CD the build should be optimized to the hardware available. The 2 primary
considerations are CPU and RAM.

Most often it is optimal to match the number of threads to the number of cores to limit context switching. Depending on
workload, for N cores, a N-1 thread limit can be used if there are heavy background/system threads running concurrently,
aswell a N+1 thread limit can be used if thread tasks are short lived. After an optimal N is determined, an explicit
declaration in the _build.sbt_ can
ensure [parallel execution in SBT](https://www.scala-sbt.org/1.x/docs/Parallel-Execution.html)
matches our expectations.

```sbt
Compile / parallelExecution := true

Global / concurrentRestrictions := {
  val max = java.lang.Runtime.getRuntime.availableProcessors()
  Seq(
    Tags.limit(Tags.CPU, max),
  )
}
```

We are working under the assumption that each of your SBT projects will run into serialization bottlenecks, meaning each
project will eventually be consuming only a single thread

#### GitHub Actions CI/CD

GitHub Actions compile code
on [dedicated runners](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources)
that have _ubuntu-latest_ provisioned at 2 core, 7 GB RAM, 14 GB SSD.

The _SBT_OPTS_ environmental variable within the action YML file should be set to allow SBT to use all available
resources.

```yaml
name: ..
env:
  SBT_OPTS: "-Xmx6G"
jobs:
  build: ...
```

#### Idea IntelliJ

When compiling locally an optimal resource allocation may need to be restricted to allow other applications to continue
to function.
IntelliJ has its own resource requirements, so depending on the machine configuration the compile settings may be lower
than on a dedicated CI/CD runner.

```
SBT_OPTS="-Xmx12G -XX:+UseParallelGC" sbt
```

## JVM GC Alternatives

There aren't many Scala 3 compiler parameters we can affect; one is the besides allocating more memory one is
There are alternative JVM garbage collectors that may offer better performance by prioritizing different factors.
Currently, the JVM has 4 available GC implementations:

- Serial Garbage Collector (_+UseSerialGC_)
- Parallel Garbage Collector (_+UseParallelGC_)
- G1 Garbage Collector (_+UseG1GC_)
- Z Garbage Collector (_+UseZGC_)

There is [more information available](https://www.baeldung.com/jvm-garbage-collectors), the parallel garbage
collector has multiple configuration options and focuses on throughput over latency making it an interesting choice to
experiment with.

## Forced Parallelized Builds using Sub-Projects

SBT will parallelize compilation and many projects won't require intrusive refactoring to allow compiler multithreading.
However, it appears that macros represent an edge case where Scala 3 can't always figure things dependencies. Quill
query macros would not be compiled in parallel whether in separate methods, separate classes or separate packages.

To force parallelization of macro compilation, the significant code change was made to break-up queries into independent
SBT projects.

![Parallel Task Console Output](/assets/images/2023/11/parallel_task_console.png)

### Basic Sub-Project Template

Scala 3 projects can be [minimally defined](https://docs.scala-lang.org/scala3/book/tools-sbt.html) with only 2
additional SBT build files:

```
$ tree
.
├── build.sbt
├── SQLUser.scala
└── project
    └── build.properties
```

The `build.properties` simply contains `sbt.version=1.9.6`

SBT sub-projects will inherit properties from the root project so the `build.sbt` will only need to define
project dependencies. Our typical `build.sbt` used for each Quill sub-project is similiar to:

```sbt
name := "sqluser"
version := "0.1.0-SNAPSHOT"
organization := "app.tradeaudit.tradeauditserver.sqldb"

scalaVersion := "3.3.1"

val javaVersion = "17"

lazy val sqlcommon = RootProject(file("../sqlcommon"))

lazy val sqluser = (project in file("."))
  .dependsOn(sqlcommon)
  .aggregate(sqlcommon)
  .settings(
    scalacOptions ++= {
      Seq(
        "-encoding", "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        "-language:experimental.macros",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Ykind-projector",
        "-Ysafe-init",
        // "-Xfatal-warnings"
      )
    },
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
  )

```

### Root Project Structure

The `build.sbt` in the root folder contains the diamond pattern project structure, with common dependencies containing
in the `sqlcommon` project, fanning out to `sql*` projects, and then aggregated again in `root`.

```sbt
//Compile time 5:03
lazy val sqlevent = RootProject(file("modules/sqlevent"))
//Compile time 2:18
lazy val sqlsocial = RootProject(file("modules/sqlsocial"))
//Compile time 1:09
lazy val sqlsocialevent = RootProject(file("modules/sqlsocialevent"))
//Compile time 0:14
lazy val sqluser = RootProject(file("modules/sqluser"))
//Compile time 0:11
lazy val sqltwitter = RootProject(file("modules/sqltwitter"))
//Compile time 0:15
lazy val sqlweb = RootProject(file("modules/sqlweb"))
//Compile time 0:34
lazy val sqltag = RootProject(file("modules/sqltag"))
//Compile time 0:36
lazy val sqlnote = RootProject(file("modules/sqlnote"))
//Compile time 0:10
lazy val sqlstockprice = RootProject(file("modules/sqlstockprice"))
//Compile time 0:14
lazy val sqleventseries = RootProject(file("modules/sqleventseries"))

lazy val root = (project in file("."))
  .dependsOn(
    sqlevent, sqlsocial, sqlsocialevent, sqluser, sqltwitter, 
    sqlweb, sqltag, sqlnote, sqlstockprice, sqleventseries
  )
  .aggregate(    sqlevent, sqlsocial, sqlsocialevent, sqluser, sqltwitter, 
    sqlweb, sqltag, sqlnote, sqlstockprice, sqleventseries
  )
```

See the [SBT documentation](https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Aggregation)  on how `aggregate` is
different than `dependsOn`.

The project order used in `aggregate` is important, placing longest running sub-projects first allowing them to begin
immediately. SBT will execute `compile` tasks in separate threads starting from left-to-right when no other dependencies
preventing it. Once threads complete, they will begin to pick up the smaller projects later in the order.

# Limitations and Downsides

Approach is very similar to a microservice approach, has the same benefit of separation of concerns,
but same downsides:

- Increased maintenance
- Initiation overhead
- Parallel execution continues to run if an exception is encountered.

This example is a diamond pattern where all sub-projects are aggregated into the same root,
but could easily turn this codebase into a multi-headed project.

## Compile Times with and without Sub-Projects

| Sub-Projects | Parallel GC | Default GC |
|:-------------|------------:|-----------:|
| No           |       13:53 |      13:39 |
| Yes          |       10:53 |       9:23 |

Breaking down our Quill queries into separate projects has net a 30% reduction in compile times.
Heap required was reduced from +6G to under 3GB.





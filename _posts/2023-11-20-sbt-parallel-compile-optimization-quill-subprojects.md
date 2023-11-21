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

But this is a trade-off to the functionality provided by the compiler in the areas of developer productivity and
runtime reliability that have no equivalent in Java. As Scala 2 matured the compiler optimizations brought compile times
down,
but Scala 3 reset the gains to zero. Macro libraries such as [Quill](https://zio.dev/zio-quill/) provide great
compile-time
features, and this article dives into optimizations beyond the compiler that can be done to reduce compilation times
in Scala 3.
<!--more-->

{% include table-of-contents.html %}

# Effect on Compile Time by moving from Scala 2 to Scala 3

Quill trades off compile time for greater runtime performance. The sample project has 120 queries; and outlined in the
previous post [ZIO Migration from Akka and Scala Futures]({% post_url 2023-10-31-zio-migration %}).

| Implementation | Scala 2 | Scala 3 |
|:---------------|:-------:|:-------:|
| Slick          |   45s   |   n/a   |
| Quill          | 4m 17s  | 8m 42s  |
| Quill Dynamic  |   n/a   | 7m 52s  |

8-core 4200MHz, 16GB RAM
Quill required > 5GB Heap to compile Scala 3

_todo:_ Scala 3 large method issue

# Quill Heap Requirements

```
[warn] In the last 6 seconds, 5.721 (98.9%) were spent in GC. [Heap: 0.65GB free of 5.21GB, max 5.21GB] Consider increasing the JVM heap using `-Xmx`
or try a different collector, e.g. `-XX:+UseG1GC`, for better performance.
```

```
[warn] In the last 10 seconds, 6.884 (70.9%) were spent in GC. [Heap: 1.84GB free of 3.34GB, max 5.33GB] Consider increasing the JVM heap using `-Xmx` or try a different collector, e.g. `-XX:+UseG1GC`, for better performance.
```

## JVM GC Alternatives

There are alternative JVM garbage collectors that may offer better performance by prioritizing different factors.
Currently, the JVM has 4 available GC implementations:

- Serial Garbage Collector (_+UseSerialGC_)
- Parallel Garbage Collector (_+UseParallelGC_)
- G1 Garbage Collector (_+UseG1GC_)
- Z Garbage Collector (_+UseZGC_)

There is [more information available](https://www.baeldung.com/jvm-garbage-collectors), the parallel garbage
collector has multiple configuration options and focuses on throughput over latency making it an interesting choice to
experiment with.

## Optimizing for the Build Server Hardware

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

### GitHub Actions CI/CD

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

### Idea IntelliJ

When compiling locally an optimal resource allocation may need to be restricted to allow other applications to continue
to function.
IntelliJ has its own resource requirements, so depending on the machine configuration the compile settings may be lower
than on a dedicated CI/CD runner.

```
SBT_OPTS="-Xmx12G -XX:+UseParallelGC" sbt
```

_todo_

# Enforcing Parallelization using SBT Sub-Projects

_todo:_ Folder structure before
_todo:_ Folder structure after

```
/helloworldproject


```

Minimal project structure required,
/project/build.properties
/build.sbt
/src/main/scala/...

```sbt
lazy val sqlhardest = RootProject(file("modules/sqlhardest"))
lazy val sqlharder = RootProject(file("modules/sqlharder"))
lazy val sqlregular1 = RootProject(file("modules/sqlregular1"))
lazy val sqlregular2 = RootProject(file("modules/sqlregular2"))
lazy val sqlregular3 = RootProject(file("modules/sqlregular3"))
lazy val sqllight1 = RootProject(file("modules/sqllight1"))
lazy val sqllight2 = RootProject(file("modules/sqllight2"))
lazy val sqllight3 = RootProject(file("modules/sqllight3"))
lazy val sqllight4 = RootProject(file("modules/sqllight4"))

lazy val root = (project in file("."))
  .dependsOn(
    sqlhardest, sqlharder, sqlregular1, sqlregular2,
    sqlregular3, sqllight1, sqllight2, sqllight3, sqllight4
  )
  .aggregate(
    sqlhardest, sqlharder, sqlregular1, sqlregular2,
    sqlregular3, sqllight1, sqllight2, sqllight3, sqllight4
  )
```

See the [SBT documentation](https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Aggregation)  on how aggregate is
different than `dependsOn`.

Order is important, SBT will execute these tasks in separate threads starting from left-to-right, so
longest running sub-project should be first, allowing smaller projects to be picked up as soon as threads become
available
(should the number of sub-projects exceed the number of CPU cores available).

Could be as simple as breaking out a single query into its own subproject to allow the reset of the project code
to compile in parallel.

### Limitations and Downsides

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





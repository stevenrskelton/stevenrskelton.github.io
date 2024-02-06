---
title: "Compiling Scala Native in a Github Action; Alternatives to GraalVM"
categories:
  - Scala
tags:
  - SBT
  - GitHub
excerpt_separator: <!--more-->
---

Scala Native is a compiler and JDK written in Scala with the goal of removing Scala's dependency on the JVM. This isn't
meant to achieve a higher performance such as with JDKs, and it is targeting a specialized use-case not considered to be
today's typical Scala development. Its competitors are Rust and Go, not GraalVM, Java or Kotlin. This article goes
through common steps and challenges encountered when compiling Scala Native for linux with a GitHub Action.<!--more-->

After 7 years [Scala Native](https://scala-native.org/) is at pre-production
maturity [version 0.4.17](https://scala-native.org/en/stable/changelog/0.4.17.html).

{% include table-of-contents.html height="100px" %}

# Different versions of OpenJDK

To avoid confusion between Scala Native, and Scala compiled natively it's important to be clear on the goals of
alternative OpenJDK implementations. Initially Oracle released Java and Java's JDK/JRE but licencing has changed
causing open source, free to use, alternatives to emerge that are compatible with the Java SE Specification.

From a high level, [whichjdk.com](https://whichjdk.com/) highlights the JDK products supported by various cloud
computing entities; AWS, Azure, IBM, SAP, and RedHat all have JDKs. There are minor differences such as in their
Garbage Collector tuning, but for the most part they are unremarkable in their similarity.

## Eclipse OpenJ9

[Eclipse OpenJ9](https://eclipse.dev/openj9/) is a cloud optimized JDK which rethinks OpenJDK implementation while
offering full compatibility. With focus on JVM startup times and memory management, the notable new features include:

- [Checkpoint/Restore in Userspace (CRUI)](https://blog.openj9.org/2022/10/14/openj9-criu-support-a-look-under-the-hood/),
- [Class Data Sharing](https://eclipse.dev/openj9/docs/shrc/),
- partial Ahead-of-Time (AOT) compilation, and
- aggressive GC tuning.

## GraalVM

The goal of [GraalVM](https://www.graalvm.org/) is of Java using strictly Ahead-of-Time (AOT) compilation, rather than
using Java's [Just-in-Time Compilation](https://en.wikipedia.org/wiki/Just-in-time_compilation). This is a breaking
change but allows compilation to a native executable rather than Java's class bytecode.

Scala has 2 SBT plugins with GraalVM support:

- [SBT Native Image](https://github.com/scalameta/sbt-native-image)
- [SBT Native Packager](https://github.com/sbt/sbt-native-packager)

The benefits of GraalVM native executable is removal of the JVM initialization time and the removal of any unreachable
code, resulting in smaller packages which boot quickly. Unfortunately the largest downside to the removal of JIT is
removal of any runtime performance optimization based on use. The AOT is able to optimize but without any visibility
into common paths and hotspots it is from a strictly static perspective. In addition, the optimizations come from
Java bytecode which has already removed some key ingredients for full static analysis. In real-world benchmarks,
GraalVM performance typically lags behind JIT competitors for long running tasks.

Another breaking change in GraalVM use is the inability to use Java reflection and runtime code generation. This is
more common in Java than in languages such as Scala, but any program can be affected. There are 2 approaches to work
around this limitation:

### Quarkus and optimized libraries written without JIT requirements

The popular Kubernetes focused framework [Quarkus](https://quarkus.io/) avoids JIT with specialized versions of standard
libraries. The changes necessary can be minor, allowing for a well-supported ecosystem of popular libraries available
as extensions to Quarkus, without notable lag to official library releases. However, support for any library not
available as an extension will rely on the second JIT alternative.

### Reflection Metadata Collection and Code Hints

The GraalVM compiler can perform static analysis of code to learn about certain types of JIT and reflection, but runtime
observation with the GraalVM Tracing Agent is required. Code needs to be run under all execution paths, and the Tracing
Agent will record all usages of the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects, or class path
resources. These will then be supplied as a JSON document to the AOT on the next compilation. The obvious problem is
triggering all executable code paths during the debugging cycle, making this (theoretically) a trial and error process.

# Motivations for Scala Native

## ScalaJS, the Scala to Javascript Transcompiler

The focus of Scala Native isn't explicitly native execution, it is in the removal of the restrictive JDK dependency.
Scala Native has 2 supported outputs:

- [ScalaJS](https://www.scala-js.org/) Javascript, and
- [LLVM](https://llvm.org) executables.

ScalaJS can realistically only transcompile to Javascript if it can remove any unused overhead imposed by the JDK.
Typical Javascript use cases are web oriented, where code sizes dictate latency and observed performance.

The JDK and Java bytecode are opinionated in their interactions with other native code. The
[Java Native Interface (JNI)](https://en.wikipedia.org/wiki/Java_Native_Interface) is an unnecessary and in often a
performance restricting abstraction that is only necessary to bridge between the JRE and external native code. The
LLVM linking for native-native interactions offers low level primitives such as pointers and unsafe memory access that
can be necessary to achieve top performance. Scala Native includes additional Scala support for the low-level C/C++
primitives directly which cannot be represented in Java.

## Executables and Jars

Java can be run directly in the JRE using `java -jar`. It is essentially just as easy to run a Jar as a native
executable (assuming a JRE is installed). As noted previously, AOT executables may initialize quicker but often bested
by JIT on long-running performance. This leaves short-lived container applications such as: AWS Lambda, serverless
functions, and Kubernetes the primary target for GraalVM, with standard JDK or OpenJ9 remaining the best choice for
server instance deployments or longer running serverless. So where exactly does Scala Native fit in?

## CLI Tools and JNI-heavy applications

The benefit of a Scala Native LLVM executable is it is just another C/C++ program. In the same way
[Rust](https://www.rust-lang.org/), [Zig](https://ziglang.org/) and [Go](https://go.dev/) have emerged as replacements
to C/C++, Scala Native offers the same compatibility and performance with a familiar and powerful Scala language. It is
the native compatibility and low-level features brought to Scala via Scala Native that make Scala Native a compelling
choice with applications which require low-level management or direct hardware interactions.

# Compiling Scala Native in a GitHub Action

As outlined previously, the use-case of Scala Native is typically a low-level CLI application. This remainder of this
article deviates from this by working through a Scala application which will not benefit from Scala Native.

**This is not a recommendation by the author to apply Scala Native to a similar project, merely a discover exercise**

## GitHub Action Runners

The linux server configurations outlining OS and installed software is
[available](https://github.com/actions/runner-images), and includes suitable CLang and LLVM versions. For local
development the Scala Native
[install guide covers](https://scala-native.org/en/stable/user/setup.html#installing-clang-and-runtime-dependencies) for
macOS (using [brew](https://brew.sh/)) and linux using apt.

## Compiling Additional C Libraries with CMake

It is very likely that a Scala Native will have a library dependency not installed on the GitHub Runner. In our
application the [FS2](https://fs2.io/) functional library requires the Amazon
[AWS TLS/SSL library S2N](https://github.com/aws/s2n-tls). Security and cryptography libraries are likely to be native
dependencies both because they likely already exist as high-performance native implementations, and secondly because
Scala Native JDK may omit them due to [security concerns around implementation](#Partial-JVM-Implementations).

Compiling a C application with CMake is a straight-forward addition to the GitHub Action YML:

```yaml
- name: Compile and Install AWS S2N-TLS
  run: |
    # clone s2n-tls
    git clone --depth 1 https://github.com/aws/s2n-tls.git
    cd s2n-tls
    # build s2n-tls
    cmake . -Bbuild \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX=./s2n-tls-install
    cmake --build build -j $(nproc)
    CTEST_PARALLEL_LEVEL=$(nproc) ctest --test-dir build
    cmake --install build
```

## Linking Custom C Library Paths in SBT

The
require additional library compilations
https://fs2.io
https://github.com/aws/s2n-tls
https://github.com/armanbilge/epollcat/

# Problems Encountered

## Multithreading

## Library Dependencies

`apt-get install`

https://github.com/portable-scala/sbt-crossproject

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.typelevel" %% "log4cats-core" % "2.6.0",
  "co.fs2" %% "fs2-io" % "3.9.4",
  "org.scala-lang.modules" %% "scala-xml" % "2.2.0",
  "org.scalatest" %% "scalatest" % "3.3.0-alpha.1" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
)
```

```scala
libraryDependencies ++= Seq(
  "com.armanbilge" %%% "epollcat" % "0.1.4",
  "org.http4s" %%% "http4s-ember-client" % http4sVersion,
  "org.http4s" %%% "http4s-ember-server" % http4sVersion,
  "org.http4s" %%% "http4s-dsl" % http4sVersion,
  "org.typelevel" %%% "log4cats-core" % "2.6.0",
  "co.fs2" %%% "fs2-io" % "3.9.4",
  "org.scala-lang.modules" %%% "scala-xml" % "2.2.0",
  "org.scalatest" %%% "scalatest" % "3.3.0-alpha.1" % Test
)

```

## Debugging

{%
include figure
image_path="/assets/images/2024/02/lldb-gui.jpg"
class="figsvgpadding"
alt="LLDB GUI"
caption="Debugging Scala Native LLVM code using LLDB Gui"
%}

## Partial JVM Implementations

https://www.scala-native.org/en/latest/lib/javalib.html

https://github.com/scala-native/scala-native/blob/main/javalib/src/main/scala/java/security/MessageDigest.scala

```scala
  def getInstance(algorithm: String): MessageDigest = new DummyMessageDigest(algorithm)
```

## CLang Library Linking

`LD_LIBRARY_PATH`, `LIBRARY_PATH`
`CMAKE_EXE_LINKER_FLAGS` `LDFLAGS`

```scala
nativeLinkingOptions += s"-L/home/runner/work/http-maven-receiver/http-maven-receiver/s2n-tls/s2n-tls-install/lib"
```

{%
include github_project.html
name="HTTP Maven Receiver"
url="https://github.com/stevenrskelton/http-maven-receiver"
description="See the Complete Code on GitHub"
%}

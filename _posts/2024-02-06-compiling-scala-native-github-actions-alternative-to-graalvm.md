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

{% include table-of-contents.html height="900px" %}

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

## Compiling additional C libraries with CMake

It is very likely that a Scala Native will have a library dependency not installed on the GitHub Runner. In our
application the [FS2](https://fs2.io/) functional library requires the Amazon
[AWS TLS/SSL library S2N](https://github.com/aws/s2n-tls). Security and cryptography libraries are likely to be native
dependencies both because they likely already exist as high-performance native implementations, and secondly because
Scala Native JDK may omit them due to [security concerns around implementation](#cryptography-and-javasecurity-package).

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

## Linking C library paths in SBT

When linking to a GitHub Action compiled C libraries such as S2N, the library needs to be in a path scanned by LD.
Because LD is being called by CMake and not GCC, typical environmental variables such as `LD_LIBRARY_PATH` and
`LIBRARY_PATH` do not work.

TODO: `CMAKE_EXE_LINKER_FLAGS` `LDFLAGS`

The easiest option is to include the path in `build.sbt` though this path will differ between local development
and within the GitHub Action.

For example, within the `http-maven-receiver` project on GitHub, the runner installed S2N in the following path:

```scala
nativeLinkingOptions += s"-L/home/runner/work/http-maven-receiver/http-maven-receiver/s2n-tls/s2n-tls-install/lib"
```

## Scala Native library dependencies in SBT

The `http-maven-receiver` is a typical Scala web server application, but with compatibility to run Scala Native making
it suitable for this article. Scala Native compatibility is dictated by the availability of Scala Native versions for
all library dependencies. These are exist as separate artifacts:

- https://repo1.maven.org/maven2/co/fs2/fs2-core_3/3.9.4/fs2-core_3-3.9.4.jar (1.5MB)
- https://repo1.maven.org/maven2/co/fs2/fs2-core_native0.4_3/3.9.4/fs2-core_native0.4_3-3.9.4.jar (3.7MB)

This is indicated in `build.sbt` by using the `%%%` operator instead of `%` / `%%`:

*Typical Scala:*

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

*Scala Native:*

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

The `%%%` operator and SBT `nativeLink` task to compile the native executables/libraries are added by the
[SBT Scala Native](https://scala-native.org/en/stable/user/sbt.html#sbt-settings-and-tasks) plugin, and additional
configuration options are straight forward parameters covered in the setup documentation.

## Code Changes Required to Support Scala Native

#### Cross Compiling

There exists the [SBT-CrossProject](https://github.com/portable-scala/sbt-crossproject) plugin that will hopefully
alleviate issues with compiling to Scala 3 Jar and Scala 3 Native.

TODO: Examples on this working.

### Partial JVM Implementations

#### Multithreading and Runtime Environment

The Scala Native runtime environment is a very basic implementation lacking a threading model and thread
synchronization primitives. The current recommendation is to import and use necessary C libraries. To achieve basic
functionality libraries such as FS2 require an additional library to operate in Scala Native, such as the I/O runtime
[epollcat](https://github.com/armanbilge/epollcat).

Scala libraries are typically built around event-loops and/or monads, so the use of NodeJS's I/O event library
[libuv](https://libuv.org/) has been made available
as [Scala Native Loop](https://github.com/scala-native/scala-native-loop).

#### Cryptography and `java.security` package

While the Scala Native lists many JDK packages as [implemented](https://scala-native.org/en/stable/user/lang.html),
some packages such as `java.security` have intentionally been left incomplete. This can have unexpected touch points,
so while a TLS/SSL implementation was a predictably omitted, the dummy implementation of all hash functions, including
MD5 and SHA1
in [java.security.MessageDigest](https://github.com/scala-native/scala-native/blob/main/javalib/src/main/scala/java/security/MessageDigest.scala)
was not.

```scala
object MessageDigest {

  def isEqual(digestA: Array[Byte], digestB: Array[Byte]): Boolean =
    true

  def getInstance(algorithm: String): MessageDigest =
    new DummyMessageDigest(algorithm)
}

private class DummyMessageDigest(algorithm: String)
  extends MessageDigest(algorithm) {

  override protected def engineDigest(): Array[Byte] = Array.empty

  override protected def engineReset(): Unit = ()

  override protected def engineUpdate(input: Byte): Unit = ()

  override protected def engineUpdate(
                                       input: Array[Byte],
                                       offset: Int,
                                       len: Int
                                     ): Unit = ()
}
```

# Runtime Observations

## Performance and Memory

For a cross-compiled project, the `http-maven-reciever` project compiled to an 27.9MB executable "Fat Jar".
The debug-mode Scala Native produced a 41.4MB linux executable.

TODO: optimized release mode file size
TODO: startup with Scala 3 Jar
TODO: load benchmarks with Scala 3 Jar
TODO: memory foot print with Scala 3 Jar

## Unit Tests

Scala Native officially supports the major testing libraries.
TODO: running tests

## Debugging

The debugging of native executables and libraries is not as developer friendly as JIT code. There is less debugging
information available within the executables for IDEs to interpret. A typical debugging experience using
[LLDB](https://lldb.llvm.org/) is dramatically different than JVM tooling; meaning cross compilation has practical
utility within the entire software development lifecycle.
{%
include figure
image_path="/assets/images/2024/02/lldb-gui.jpg"
class="figsvgpadding"
alt="LLDB GUI"
caption="Debugging Scala Native LLVM code using LLDB Gui"
%}

# Conclusions

Scala Native is a welcome addition to the C/C++ alternatives for Scala developers. While it is undeniably not as popular
as Rust, it has room to grow as this ecosystem grows in importance. Modern system hardware is approaching an era of 
virtually unlimited parallelism and throughput, putting emphasis on the only performance avenue left: latency. 
Low-level languages will need to solve an increasing diversity of problems, and for many tasks Scala Native will be
a compelling choice.

{%
include github_project.html
name="HTTP Maven Receiver"
url="https://github.com/stevenrskelton/http-maven-receiver"
description="See the Complete Code on GitHub"
%}

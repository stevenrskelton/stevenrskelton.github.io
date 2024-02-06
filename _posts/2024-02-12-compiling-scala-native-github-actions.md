---
title: "Compiling Scala Native in a Github Action"
categories:
  - Scala
tags:
  - SBT
  - GitHub
excerpt_separator: <!--more-->
---

Scala Native is a compiler for Scala which removes the dependency on the JVM. While 7 years old it is at 
pre-production maturity at version 0.4.17. Challenges remain with compiling, choice of compatible libraries is limited
to specially ported Scala code, and the compiler hasn't undergone the same optimizations as comparable JDK runtimes.
The maturity of Scala Native has progressed past the ability to be compiled in MacOS, Windows and as outlined in this 
article, linux using GitHub Actions.<!--more-->

{% include table-of-contents.html height="100px" %}

# Competition to Native Executables

Main motivation is to compile without JVM dependencies, not limited to native execution. Alternative outputs to any LLVM 
environment, including the Javascript using ScalaJS.

## Executable Jars

Running with `java -jar`

## GraalVM

The practical uses of [Scala Native](https://scala-native.org) is currently CLI applications. For native JVM compilation
its main competitor [GraalVM](https://www.graalvm.org/) is a much better choice; with [Quarkus](https://quarkus.io/)
offering [Jakarta EE](https://jakarta.ee/) and [Microprofile](https://microprofile.io/) compatibility and a rich
set of JVM libraries including Scala 3 support.

## Eclipse OpenJ9 JDK

https://eclipse.dev/openj9/



# Initial Setup 

## GitHub Action Runners

https://github.com/actions/runner-images


## LLVM Install

//brew install llvm



# Compiling Additional C Libraries with CMake

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
  "org.http4s"              %% "http4s-ember-client"  % http4sVersion,
  "org.http4s"              %% "http4s-ember-server"  % http4sVersion,
  "org.http4s"              %% "http4s-dsl"           % http4sVersion,
  "org.typelevel"           %% "log4cats-core"        % "2.6.0",
  "co.fs2"                  %% "fs2-io"               % "3.9.4",
  "org.scala-lang.modules"  %% "scala-xml"            % "2.2.0",
  "org.scalatest"           %% "scalatest"            % "3.3.0-alpha.1"   % Test,
  "org.typelevel"           %% "cats-effect-testing-scalatest" % "1.5.0"  % Test,
)
```

```scala
libraryDependencies ++= Seq(
  "com.armanbilge"          %%% "epollcat"            % "0.1.4",
  "org.http4s"              %%% "http4s-ember-client" % http4sVersion,
  "org.http4s"              %%% "http4s-ember-server" % http4sVersion,
  "org.http4s"              %%% "http4s-dsl"          % http4sVersion,
  "org.typelevel"           %%% "log4cats-core"       % "2.6.0",
  "co.fs2"                  %%% "fs2-io"              % "3.9.4",
  "org.scala-lang.modules"  %%% "scala-xml"           % "2.2.0",
  "org.scalatest"           %%% "scalatest"           % "3.3.0-alpha.1" % Test
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

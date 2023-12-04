---
title: "The Many Different Forms of Parallelism and Concurrency"
categories:
  - Scala
tags:
  - AsyncExecution
  - ZIO
excerpt_separator: <!--more-->
---
There are different concepts of parallelism and concurrency that exist and need to be considered when architecting
software applications. The abstractions offered by libraries and frameworks to hide parallelism can be convenient
ways to simplify code, but without understanding each parallelism concept it is easy to introduce errors or fail to
optimally perform in distributed environments.<!--more-->

{% include table-of-contents.html height="400px" %}


https://zio.dev/guides/interop/with-future/
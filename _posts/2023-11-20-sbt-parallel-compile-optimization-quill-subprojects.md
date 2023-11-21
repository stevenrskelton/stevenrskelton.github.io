---
title: "SBT Parallel Compile Optimizations using Quill Sub-Projects"
categories:
  - Scala
tags:
  - SBT
excerpt_separator: <!--more-->
---

{% include postlogo.html 
    title="XKCD" 
    src="/assets/images/2023/11/compiling.png" 
    url="https://xkcd.com/303" 
    caption="My code's compiling."
%}

Early criticisms of Scala was compiling was slow compared to Java, and especially interpreted languages such as Javascript 
or Python where compile times aren't a consideration. Long compile times can interrupt developer flow in addition to the 
actual developer downtime during the wait. As Scala 2 matured the compiler got faster, but Scala 3 reset the gains to zero.
Challenging compile-time macro libraries such as [Quill](https://zio.dev/zio-quill/) are a large trade-off of additional compile
time for runtime performance and stability; this article dives into optimizations to compile time that can be performed outside of the compiler.
<!--more-->

{% include table-of-contents.html %}



Compile with ordered subprojects
- (Parallel compile): 10:53
- (Default GC compile): 9:23
 
Compile without subprojects
- (Parallel compile): 13:53
- (Default GC compile): 13:39



### Effect on Compile Time

Quill trades off compile time for greater runtime performance.  The sample project has 80 queries;

| Implementation | Scala 2 Compile Time | Scala 3 Compile Time |
|:---------------|:--------------------:|:--------------------:|
| Slick          |         45s          |         n/a          |
| Quill          |        4m 17s        |        8m 42s        |
| Quill Dynamic  |         n/a          |        7m 52s        |

8-core 4200MHz, 16GB RAM
Quill required > 4GB Heap to compile Scala 3
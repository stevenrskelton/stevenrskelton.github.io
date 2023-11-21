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
    caption="Scala is slow to compile."
%}

But this is a trade-off to the functionality provided by the compiler in the areas of developer productivity and 
runtime reliability that have no equivalent in Java. As Scala 2 matured the compiler optimizations brought compile times down,
but Scala 3 reset the gains to zero. Macro libraries such as [Quill](https://zio.dev/zio-quill/) provide great compile-time 
features, and this article dives into optimizations outside of the compiler that can be done to reduce compilation times in Scala 3.
<!--more-->

{% include table-of-contents.html %}


## Sub-Project Compile
Compile with ordered subprojects
- (Parallel compile): 10:53
- (Default GC compile): 9:23
 
Compile without subprojects
- (Parallel compile): 13:53
- (Default GC compile): 13:39

Problems with GC
```
[warn] In the last 6 seconds, 5.721 (98.9%) were spent in GC. [Heap: 0.65GB free of 5.21GB, max 5.21GB] Consider increasing the JVM heap using `-Xmx`
or try a different collector, e.g. `-XX:+UseG1GC`, for better performance.
```

Optimal GC settings
```
SBT_OPTS="-Xmx12G -XX:+UseParallelGC" sbt
```

### Effect on Compile Time

Quill trades off compile time for greater runtime performance.  The sample project has 80 queries;

| Implementation | Scala 2 Compile Time | Scala 3 Compile Time |
|:---------------|:--------------------:|:--------------------:|
| Slick          |         45s          |         n/a          |
| Quill          |        4m 17s        |        8m 42s        |
| Quill Dynamic  |         n/a          |        7m 52s        |

8-core 4200MHz, 16GB RAM
Quill required > 4GB Heap to compile Scala 3
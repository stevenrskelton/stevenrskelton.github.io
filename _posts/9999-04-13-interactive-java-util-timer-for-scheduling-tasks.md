---
title: "Interactive java.util.timer for Scheduling Tasks"
published: false
categories:
  - Scala
---
My last blog update was 6 years ago, and Javascript still seems popular. Is this a case of _There Is No Alternative_?

Intentionally single threaded.  For a multithreaded, the [java.util.concurrent.ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) provides every needed to control how threads are managed between the tasks.  A simple `java.util.Timer` could be used as a simple scheduler, puttings tasks into the `ExecutorService` for execution when it is the right time.

## Javascript and HTML
https://examples.javacodegeeks.com/java-scheduling-example/
https://dzone.com/articles/executorservice-10-tips-and
https://stackoverflow.com/questions/42276707/how-to-use-return-value-from-executorservice
https://github.com/enragedginger/akka-quartz-scheduler
https://www.jenkins.io/

{%
  include downloadsources.html
  src="/assets/images/2022/04/InteractiveJavaUtilTimer.scala,/assets/images/2022/04/InteractiveJavaUtilTimerSpec.scala"
%}

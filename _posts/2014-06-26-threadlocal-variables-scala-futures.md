---
#layout: post
title: "ThreadLocal Variables and Scala Futures"
categories:
  - Scala
tags:
  - AsyncExecution
excerpt_separator: <!--more-->
---

[Thread-Local storage (TLS)](http://en.wikipedia.org/wiki/Thread-local_storage)
allows static variables to be attached to the currently executing thread. The most common use of TLS is to allow global
context to be available throughout the entire call stack without passing it explicitly as a method parameters. In a
web-application, this allows contextual request metadata, such as the URL, to be referenced anywhere within the code
handing the request; which is extremely useful for logging or auditing purposes.<!--more-->

{% include table-of-contents.html height="200px" %}

Where TLS can fail is when the execution path is handled by multiple threads.
Anywhere [Futures](http://docs.scala-lang.org/overviews/core/futures.html) parallelize code,
execution is handled off to a different thread defined within the `Executor` thread-pool. This means that any use
of Scala Futures or similiar async execution techniques will often lose TLS data. Since the `Future` is at the heart of
[Reactive web frameworks](http://www.reactivemanifesto.org/) such as [Play! 2.0](http://www.playframework.com/)
alternative techniques or code to handle TLS propagation is required.

# Thread-Local Storage Propagation

A simple mechanism to propagate TLS is using
an [ExecutionContext](https://github.com/scala/scala/blob/2.12.x/src/library/scala/concurrent/ExecutionContext.scala)
trait. The `ExecutionContext` is an abstraction over an entity to manage execution blocks of program logic (`Work`).,
and it is an _implicit_ requirement to create a Future:

```scala
import scala.concurrent.ExecutionContext.Implicits.global

val f = Future {
  /*this block executes in another thread*/
}
```

The most common implementation of an `ExecutionContext` is
the [ForkJoinPool](https://github.com/scala/scala/blob/2.12.x/src/forkjoin/scala/concurrent/forkjoin/ForkJoinPool.java),
which is an advancement over a basic thread-pool implementing an efficient work-stealing algorithm. It is the default
for parallelized applications built around [Play!](http://www.playframework.com/) and [Akka](http://akka.io/).

Let’s look at a small program which prints out basic thread information:

```scala
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

def printThreadInfo(id: String) = println {
  id + " : " + Thread.currentThread.getName
}

implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

printThreadInfo("main")
val fut1 = Future {
  printThreadInfo("fut1")
}

Await.result(fut1, 1.second)

//Output:
//> main : main
//> fut1 : ForkJoinPool-1-worker-13
```

The `Future` runs on a different thread from the main. Can we store different values in TLS?

## DynamicVariable and ThreadLocal to store values

Scala 2 has a modified version of
Java’s [ThreadLocal](http://docs.oracle.com/javase/6/docs/api/java/lang/ThreadLocal.html)
called [DynamicVariable](http://www.scala-lang.org/api/2.10.0/scala/util/DynamicVariable.html). `DynamicVariable` is
unrelated to dynamic fields in `scala.language.dynamics`, it is named that way because it will dynamically store static
variables which have a dynamic value depending on the thread executing.

```scala
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.DynamicVariable

def printThreadInfo(id: String) = println {
  id + " : " + Thread.currentThread.getName + " = " + dyn.value
}

//create a dynamic variable
val dyn = new DynamicVariable[Int](0)

implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

val fut1 = dyn.withValue(1) {
  Future {
    printThreadInfo("fut1")
  }
}
val fut2 = dyn.withValue(2) {
  Future {
    printThreadInfo("fut2")
  }
}
val fut3 = dyn.withValue(3) {
  Future {
    printThreadInfo("fut3")
  }
}

Await.result(fut1, 1.second)
Await.result(fut2, 1.second)
Await.result(fut3, 1.second)

//Output:
//> fut1 : ForkJoinPool-1-worker-13 = 1
//> fut2 : ForkJoinPool-1-worker-11 = 2
//> fut3 : ForkJoinPool-1-worker-9 = 3

//But wait, threads work when created, what happens if we reuse threads already in the pool?

val fut4 = dyn.withValue(4) {
  Future {
    printThreadInfo("fut4")
  }
}
val fut5 = dyn.withValue(5) {
  Future {
    printThreadInfo("fut5")
  }
}

Await.result(fut4, 1.second)
Await.result(fut5, 1.second)

//Output:
//> fut4 : ForkJoinPool-1-worker-11 = 2
//> fut5 : ForkJoinPool-1-worker-11 = 2
```

This code encounters the problem that `DynamicVariable` will correctly pass on TLS to new threads, but if the thread has
already been created and is being re-unsed from the pool the TLS won’t be copied.  The values will have the old value 
assigned during its previous use.

## Modified ExecutionContext to propagate values

The `ExecutionContext` handles all thread scheduling, can we implement logic into the `ExecutionContext` to copy our 
TLS into the threads before they execute? The trait is very simple: `execute` can be modified to include these changes:

```scala
/**
 * An `ExecutionContext` is an abstraction over an entity that can execute program logic.
 */
trait ExecutionContext {

  /** Runs a block of code on this execution context.
   */
  def execute(runnable: Runnable): Unit

  /** Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit

  /** Prepares for the execution of a task. Returns the prepared
   * execution context. A valid implementation of `prepare` is one
   * that simply returns `this`.
   */
  def prepare(): ExecutionContext = this

}
```

We are using the `ForkJoinPool` implementation, so it is the base used to inherit from. If we send
a `DynamicVariable` as a constructor parameter, we won’t have to worry about closures.

```scala
import scala.concurrent.forkjoin._

class ForkJoinPoolWithDynamicVariable[T](dynamicVariable: DynamicVariable[T])
  extends ForkJoinPool {

  override def execute(task: Runnable) {
    //need to inject dynamicVariable.value into task
    super.execute(task)
  }

}
```

So the `execute` runs in the main thread, but the `task` is run in the `Future`‘s thread-pool. We somehow need to inject
the `dynamicVariable` inside `Runnable`. Let’s make another `Runnable` which has closure on `dynamicVariable`‘s value,
and then runs `task`.

```scala
override def execute(task: Runnable) {

  val copyValue = dynamicVariable.value
  super.execute(new Runnable {
    override def run = {
      dynamicVariable.value = copyValue
      task.run
    }
  })

}
```

Basically, the `copyValue` reads the `dynamicVariable` in the main thread, then while `run` is executed in a thread-pool
thread it will assign the proper value to the `dynamicVariable`. As `T` is generic, it can be any Scala class, including
a `Map`, so one `DynamicVariable` is flexible enough for most scenarios. All we are left to do is to use our
new `ExecutorService`, so replace:

```scala
implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
```

With the new class:

```scala
val dyn = new DynamicVariable[T](/* default for T */)

implicit val executionContext = scala.concurrent.ExecutionContext.fromExecutorService(
  new ForkJoinPoolWithDynamicVariable(dyn)
)
```
## Garbage Collection
A small note about garbage collection. As long as a thread exists, it will maintain references to its `ThreadLocal`
variables. If the thread-pool does not recycle threads and a thread goes back into the pool without releasing its TLS
then those objects will not be freed. Normally this isn’t an issue, however for larger objects it might be wise to
explicitly release them after use, or use
a [WeakReference](http://www.scala-lang.org/api/current/index.html#scala.ref.WeakReference) if behaviour allows.

# Article Feedback

> ⓘ This blog originally supported comments

<table class="html-bg">
<tr><td>
<p><i><a href="http://yanns.github.io/">Yann</a> on June 26, 2014 at 5:45 am said: </i></p>
<p>In the same spirit, I wrote about passing the slf4j MDC context with Future (the MDC is based on thread local variables)</p>
<p><a href="http://yanns.github.io/blog/2014/05/04/slf4j-mapped-diagnostic-context-mdc-with-play-framework">http://yanns.github.io/blog/2014/05/04/slf4j-mapped-diagnostic-context-mdc-with-play-framework</a></p>
</td></tr>
<tr><td>
<p><i>steven on June 26, 2014 at 12:22 pm said: </i></p>
<p>Very nice Yann!</p>
<p>Setting up better logging for a web application pays for itself many times over. :) Your code is eerily similiar to mine – it makes me wonder if this is missing functionality in Play….</p>
<p>When I was working with Finagle, Twitter’s Futures had ThreadLocal functionality built-in (without the need for a custom ExecutionContext). Anything saved using<br/><a href="https://github.com/twitter/util/blob/master/util-core/src/main/scala/com/twitter/util/Local.scala">https://github.com/twitter/util/blob/master/util-core/src/main/scala/com/twitter/util/Local.scala</a><br/>would be automatically copied by the Future into it’s code block using a closure. But trying to sell ThreadLocal hacks to the functional programming crowd is tough, it’s no wonder Typesafe didn’t include it in theirs.</p>
</td></tr>
<tr><td>
<p><i><a href="http://chris-wewerka.de/">Chris Wewerka</a> on September 10, 2014 at 8:08 am said: </i></p>
<p>Very nice post. @steven: as we work with finagle do you know about some doc or blog about how Local’s work there?</p>
</td></tr>
<tr><td>
<p><i>Mayumi on June 26, 2014 at 4:38 pm said:</i></p>
<p>A great post :) Thanks!</p>
</td></tr>
</table>

{%
include downloadsources.html
src="https://github.com/stevenrskelton/Blog/blob/master/src/test/scala/ForkJoinPoolWithDynamicVariableSpec.scala"
%}

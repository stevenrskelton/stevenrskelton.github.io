---
layout: post
title: "ThreadLocal Variables and Scala Futures"
download_sources:
- https://github.com/stevenrskelton/Blog/blob/master/src/test/scala/ForkJoinPoolWithDynamicVariableSpec.scala
---

[Thread-Local storage (TLS)](http://en.wikipedia.org/wiki/Thread-local_storage) allows static variables to be attached to the currently executing thread. The most common use of TLS is to pass global context through the call-stack without method parameters. In a web-application, this will allow data (such as the current request’s URL) to be globally available throughout the codebase – extremely useful for logging or auditing purposes.

Where TLS can fail is when the execution path moves between threads. Anywhere [Futures](http://docs.scala-lang.org/overviews/core/futures.html) parallelize code, execution is handled off to a random thread from a thread-pool for async execution where all TLS is lost. `Future`s are at the heart of new [Reactive web frameworks](http://www.reactivemanifesto.org/) such as [Play! 2.0](http://www.playframework.com/), requiring everyone to rethink how TLS is done.

But the solution is rather simple, it lies within the [ExecutionContext](https://github.com/scala/scala/blob/2.12.x/src/library/scala/concurrent/ExecutionContext.scala) trait. The `ExecutionContext` is an abstraction over an entity that can execute program logic, and it is an _implicit_ requirement to create a Future:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
val f = Future { /*this block executes in another thread*/ }
```

The most common implementation is the [ForkJoinPool](https://github.com/scala/scala/blob/2.12.x/src/forkjoin/scala/concurrent/forkjoin/ForkJoinPool.java), which is an advancement over a basic thread-pool by implementing an efficient work-stealing algorithm. It is the default for parallelized applications built around [Play!](http://www.playframework.com/) and [Akka](http://akka.io/).

Let’s look at a small program which prints out basic thread information:

```scala
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
 
def printThreadInfo(id: String) = println {
  id + " : " + Thread.currentThread.getName
}
 
implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
 
printThreadInfo("main")
val fut1 = Future { printThreadInfo("fut1") }
 
Await.result(fut1, 1.second)
 
//Output:
//> main : main
//> fut1 : ForkJoinPool-1-worker-13
```

So the `Future` definitely runs on a different thread from the main. Can we store different values in TLS?

As this is Scala, let’s use the [DynamicVariable](http://www.scala-lang.org/api/2.10.0/scala/util/DynamicVariable.html) class instead of Java’s [ThreadLocal](http://docs.oracle.com/javase/6/docs/api/java/lang/ThreadLocal.html) directly. They are so named as they are static variables who’s value _dynamically_ depends on which thread is executing.

`DynamicVariable` *has nothing to do with dynamic fields in* `scala.language.dynamics`

```scala
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.DynamicVariable
 
def printThreadInfo(id: String) = println {
  id + " : " + Thread.currentThread.getName + " = " + dyn.value
}
 
//create a dynamic variable
val dyn = new DynamicVariable[Int](0)
 
implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
 
val fut1 = dyn.withValue(1) { Future { printThreadInfo("fut1") } }
val fut2 = dyn.withValue(2) { Future { printThreadInfo("fut2") } }
val fut3 = dyn.withValue(3) { Future { printThreadInfo("fut3") } }
 
Await.result(fut1, 1.second)
Await.result(fut2, 1.second)
Await.result(fut3, 1.second)
 
//Output:
//> fut1 : ForkJoinPool-1-worker-13 = 1
//> fut2 : ForkJoinPool-1-worker-11 = 2
//> fut3 : ForkJoinPool-1-worker-9 = 3
 
//But wait, threads work when created, what happens if we reuse threads already in the pool?
 
val fut4 = dyn.withValue(4) { Future { printThreadInfo("fut4") } }
val fut5 = dyn.withValue(5) { Future { printThreadInfo("fut5") } }
 
Await.result(fut4, 1.second)
Await.result(fut5, 1.second)
 
//Output:
//> fut4 : ForkJoinPool-1-worker-11 = 2
//> fut5 : ForkJoinPool-1-worker-11 = 2
```

So we run into the problem that `DynamicVariable` will correctly pass on TLS to new threads, but if the thread has already been created and re-unsed from the pool, the TLS won’t be copied, it will have the old value assigned during its previous use.

The `ExecutionContext` handles all of the thread scheduling, can we tell it to copy our TLS into the threads before they execute?
The trait is very simple: `execute` is be an obvious first choice to modify:

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
   *  execution context. A valid implementation of `prepare` is one
   *  that simply returns `this`.
   */
  def prepare(): ExecutionContext = this
 
}
```

We are using the `ForkJoinPool` implementation, so create a new inherited class to modify. If we send a `DynamicVariable` as a constructor parameter, we won’t have to worry about another closure.

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

So the `execute` runs in the main thread, but the `task` is run in the `Future`‘s thread-pool. We somehow need to inject the `dynamicVariable` inside `Runnable`. Let’s make another `Runnable` which has closure on `dynamicVariable`‘s value, and then runs `task`.

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

Basically, the `copyValue` reads the `dynamicVariable` in the main thread, then while `run` is executed in a thread-pool thread it will assign the proper value to the `dynamicVariable`. As `T` is generic, it can be any Scala class, including a `Map`, so one `DynamicVariable` is flexible enough for most scenarios. All we are left to do is to use our new `ExecutorService`, so replace:

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

A small note about garbage collection. As long as a thread exists, it will maintain references to its `ThreadLocal` variables. If the thread-pool does not recycle threads and a thread goes back into the pool without releasing its TLS then those objects will not be freed. Normally this isn’t an issue, however for larger objects it might be wise to explicitly release them after use, or use a [WeakReference](http://www.scala-lang.org/api/current/index.html#scala.ref.WeakReference) if behaviour allows.

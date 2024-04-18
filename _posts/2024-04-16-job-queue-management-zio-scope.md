---
title: "Job Queue Execution Management using ZIO Scopes"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
examples:
  - job-queue-zio-scope
---

Job Queues are critical parts of Enterprise workloads. Complex queues use distributed nodes, state machines, and
complex scheduling to trigger and track running jobs. But when simplicity allows the best approach is to create small
idempotent jobs. The smaller the unit of work the easier progress can be tracked, jobs can be restarted or rerun with
minimal waste, composability and reuse are increased, and logic is easier to reason about. These are the same arguments
for Functional Programming and their Effect Systems, such as ZIO. Effect systems are congruent to the enterprise job
queue, with ZIO fibers performing work and ZIO [Resource Management](https://zio.dev/reference/resource/)
forming the scheduling and supervision backbone. An efficient job queue can be written using ZIO constructs using
surprisingly minimal amount of code.

{% include table-of-contents.html height="400px" %}

# ZIO Resources and Scope

ZIO Resources form a contract preventing resource leaks and ensuring proper finalization of a closure. A job queue which
maintains observability of in-progress jobs has the same concern. Active jobs are open resources and need to properly
finalization after being consumed, in the same way an open file needs to be properly closed of after use.

The approach is release queue items with an attached ZIO [Scope](https://zio.dev/reference/resource/scope/). Work can
be performed within this scope, and the scope can be responsible for marking queue items either as consumed or to be
returned back into the queue due to processing failure.

{%
include figure image_path="/assets/images/2024/04/unique_job_queue.svg"
caption="Job queue using ListHashSet and ZIO Scopes to manage queue removal"
img_style="padding: 10px; background-color: white; height: 320px;"
%}

## Queue Features

- Maintain a distinct list of queue entries.  
  If objects are added multiple times the queue will only contain the first object, in its correct queue position.
- Automatically remove popped queue items after their work has been completed
  This allows work in-progress to count towards the item uniqueness. Re-adding work that is already in-progress will be
  rejected by the queue.
- Popping from the queue is a blocking operation
  There is no need to poll the queue for new items, consumers can stream items and fetch batches using thread-safe
  operations.
- There is no [dead-letter output](https://en.wikipedia.org/wiki/Dead_letter_queue)
  Items which cannot be processed are returned to the queue by the Scope exception finalizer.

### Class Interface

```scala
class DistinctZioJobQueue[A] {

  //Jobs queued for execution.
  def queued: ZIO[Any, Nothing, Seq[A]]

  //Jobs currently executing.
  def inProgress: ZIO[Any, Nothing, Seq[A]]

  //Add job to queue, will return `true` if successful. Jobs already in queue will return `false`.
  def add(elem: A): ZIO[Any, Nothing, Boolean]

  //Add jobs to queue. Will return all jobs that failed to be added.
  def addAll(elems: Seq[A]): ZIO[Any, Nothing, Seq[A]]

  //Blocks until returning at least one, but no more than N, queued jobs.
  def takeUpToNQueued(max: Int): ZIO[Scope, Nothing, Seq[A]]

}
```
# ZIO Concurrency uses Fibers, Not Threads

## Using Semaphore for Concurrency

The common approach to create concurrent collections in Java is using the JDK provided
wrapper `Collections.synchronizedSet()`. This is a great mechanism for handling thread-safety, however ZIO concurrency
operates with ZIO fibers making this approach untenable. It is incorrect to block threads at any time in ZIO outside of
a `ZIO.blocking` scope because this will block all fibers using that thread. Fibers are the independent workers in ZIO,
not threads, and blocking the system thread will cause performance degradation and possible deadlocks.

The [ZIO Semaphore](https://zio.dev/reference/concurrency/semaphore/) is the ZIO equivalent mechanism to provide 
synchronization. It operates on the fiber level making it distinctly different from a JDK semaphore. The same 
concurrency concerns are still valid while using fibers to access `LinkedHashSet` methods. All write operations
must be synchronized, and all read operations can only parallelize with other read operations. Any read operation
occurring during a write operation is vulnerable to a `ConcurrentModificationException` if it its `iterator` encounters 
stale state.

## Ref and Ref.Synchronized

In addition to semaphore, ZIO provides other concurrency mechanisms such as `Ref` and `STM`. 
[Software Transactional Memory](https://zio.dev/reference/stm/) is a powerful construct however requiring specialized 
implementations of common classes, making it worthy of its own external discussion. The `Ref` construct is a very 
accessible mechanism in ZIO comparable to the `Atomic*` classes in the JDK, but at a higher level. A noticeable downside
is it is only suitable for immutable references. Our implementation uses a mutable `LinkedHashSet`.

### Ref and HashCodes 

There are fundamental differences between the Java and Scala library implementations of `LinkedHashSet`.  

#### java.util.LinkedHashSet

The Java implementation of LinkedHashSet is a mutable implementation, and modifying it will not change its hashCode. 
This means that wrapping it within either a `Ref` or `Synchronized` will cause all atomic guarantees to brake. The
atomicity is implemented using hashCode verification to detect write conflicts rather than thread synchronization to
the memory address. This is better for performance, but in this case it will effectivily behave as if there were no
write management at all.

#### scala.collection.mutable.LinkedHashSet

The Scala implementation of LinkedHashSet is a mutable implementation, however it has a dynamically computed hashCode.
This will allow it to function correctly within a Ref, but will incur a performance overhead during any writes.

```scala
override def hashCode: Int = {
  val setIterator = this.iterator
  val hashIterator: Iterator[Any] =
    if (setIterator.isEmpty) setIterator
    else new HashSetIterator[Any] {
      var hash: Int = 0
      override def hashCode: Int = hash
      override protected[this] def extract(nd: Node[A]): Any = {
        hash = unimproveHash(nd.hash)
        this
      }
    }
  MurmurHash3.unorderedHash(hashIterator, MurmurHash3.setSeed)
}
```

To align with immutable variants, the Scala Collections Library dynamically computes hashCode to allow equivalence based 
on internal elements rather than by the parent class reference. Two iterables, represented using different class 
implementations will be equal if they contain the same elements. This also means that they will be unequal if their 
elements are different. This may be ideal for smaller number of elements, but it will begin to be more performant to 
use a semaphore rather than a ref if the hashCode is slow.

### Blocking in ZIO

The primary mechanism to block ZIO fibers is by mapping from `await` on a `Promise`. When a second fiber completes
the promise using `succeed` the first fiber will unblock and resume execution. Within our queue, all consumers can await
the same activity promise. Whenever the queue state changes making queued elements available we will
call `notifyActivity` to complete the promise.

```scala
/**
 * Signal all consumers to recheck the queue
 */
private def notifyActivity: UIO[Unit] =
  for {
    resetPromise <- Promise.make[Nothing, Unit]
    _ <- promise.succeed(())
  } yield promise = resetPromise
```

The `notifyActivity` method will atomically complete the current promise, and replace it with a new uncompleted
promise. It is important to replace the `Ref` before completing the promise to avoid another thread from requesting
the same completed promise from the ref. An unbounded cycle would be possible if another fiber is triggered by the
promise completion, finds no elements, then awaits on the completed promise a second time. This cycle could continue
until the ref is replaced, which if the fiber never relinquishes control could be infinite.

The queue can contain new queued elements whenever items are added, or when they are made available from a scope
closing unsuccessfully.

```scala
def add(elem: A):
//- obtain permit to LinkedHashSet
//- try to add to queue
//- if added successfully call notifyActivity
//- release permit

def addAll(elems: Seq[A]):
//- obtain permit to LinkedHashSet
//- try to add all to queue
//- if any added successfully call notifyActivity
//- release permit

def takeUpToNQueued(max: Int):
//- obtain permit to LinkedHashSet
//- return items with Scope
//- release permit
//- Scope finalizer returns items to queue on exception then calls notifyActivity
```

#### Creating Scope using acquireReleaseExit

Creating `ZIO[Scope, ?, ?]` removal of the `Scope` from the environment requires explicitly defining the boundary of the
scope. This is commonly done using `ZIO.scope`, which is basically:

#### Closing Scope
```scala
def scope(zio: => ZIO[Scope with R, E, A]): ZIO[R, E, A]
```


//TODO:

`acquireRelease`
`acquireReleaseExit`
`acquireReleaseInterruptible` `acquireReleaseInterruptibleExit`

### Job Uniqueness using equals / hashCode

The basic requirement for this queue will be able to flag entries as either queued, or having been released within a
scope by a consumer.

```scala
private enum Status {
  case Queued, InProgress
}
```

To attach a `status` field to each entry, we can wrap entries with a `JobStatus` class, but overwrite the
`equals` and `hashCode` fields such that they will only consider the queued item, and not the current status. This
ensures that trying to add a duplicate entry will be blocked regardless of the existing item's status.

The `JobStatus` class could be expanded to support additional queue functionality. Queue extensions typically include
performance metrics: queued time, in-progress time, and operational metrics: add-collision count and failure-count from
any failed consumer executions.

```scala
private class JobStatus[A](val a: A, var status: Status) {
  override def hashCode(): Int = a.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case o: JobStatus[?] => o.a.equals(a)
    case _ => false
  }
}
```

### Queue Class and Private Members

```scala
class UniqueJobQueue[A](
                         semaphore: Semaphore,
                         promise: Promise[Nothing, Unit],
                       ) {
  val linkedHashSet = new mutable.LinkedHashSet[JobStatus[A]]
}
```

//TODO:


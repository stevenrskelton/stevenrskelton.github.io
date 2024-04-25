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
surprisingly minimal amount of code.<!--more-->

{% include table-of-contents.html height="800px" %}

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
  There is no need to poll the queue for new items, all consumers can stream items and fetch batches using thread-safe
  operations.
- For simplicity, there is no [dead-letter output](https://en.wikipedia.org/wiki/Dead_letter_queue)
  Items which cannot be processed are returned to the queue by the Scope finalizer.

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
operates with ZIO fibers making this approach untenable. It is incorrect to block threads at any time in ZIO outside
a `ZIO.blocking` scope because this will block all fibers using that thread. Fibers are the independent workers in ZIO,
not threads, and blocking the system thread will cause performance degradation and possible deadlocks.

The ZIO [Semaphore](https://zio.dev/reference/concurrency/semaphore/) is the ZIO equivalent mechanism to provide
synchronization. It operates on the fiber level making it distinctly different from a JDK semaphore. The same
concurrency concerns are still valid while using fibers to access `LinkedHashSet` methods. All write operations
must be synchronized, and all read operations can only parallelize with other read operations. Any read operation
occurring during a write operation is vulnerable to a `ConcurrentModificationException` if it its `iterator` encounters
stale state.

## Ref and Ref.Synchronized

In addition to semaphore, ZIO provides other concurrency mechanisms such as `Ref` and `STM`.
[Software Transactional Memory](https://zio.dev/reference/stm/) is a powerful construct however requiring specialized
implementations of common classes, making it worthy of its own external discussion. The `Ref` construct is a very
accessible mechanism in ZIO comparable to the `AtomicReference` class in the JDK, but at a higher level. A noticeable
downside
is it is only suitable for immutable references. Our implementation uses a mutable `LinkedHashSet`.

### Ref and Hash Codes

There are fundamental differences between the Java and Scala library implementations of `LinkedHashSet`.

#### java.util.LinkedHashSet

The Java implementation of LinkedHashSet is a mutable implementation, and modifying it will not change its hashCode.
This means that wrapping it within either a `Ref` or `Synchronized` will cause all atomic guarantees to brake. The
atomicity is implemented using hashCode verification to detect write conflicts rather than thread synchronization to
the memory address. This is better for performance, but in this case it will effectivily behave as if there were no
write management at all.

#### scala.collection.mutable.LinkedHashSet

The Scala implementation of LinkedHashSet is a mutable implementation, however it has a dynamically computed hashCode.
This will allow it to function correctly within a Ref under certain conditions. By incurring a performance overhead
during all writes it will allow the `AtomicReference.compareAndSet` method to work correctly.

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

There still remains the inability of LinkedHashSet to handle reads during writes. The primary benefit of immutability
is data never changes preventing all data instability during a read operations.

The motivation of the dynamic hashCode for the mutable LinkedHashSet in the Scala Collections Library is to allow
equality between immutable/mutable variants. Comparing a mutable Set to a immutable Set with the same internal elements
will be successful, creating a more powerful `Set` interface abstraction.

To allow the Scala LinkedHashSet to work within a `Ref` we will need to accept a hashCode calculation penalty on all
writes, as well as implement a gate around simultaneous read/write operations. This may be suitable in some situations,
but optimizing for fast reads is a more generally acceptable approach, and for these reasons our queue implementation
is better off using a semaphore directly.

# Blocking in ZIO

The primary mechanism to block ZIO fibers is by mapping from `await` on a `Promise`. When a second fiber completes
the promise using `succeed` the first fiber will unblock and resume execution. Within our queue, all consumers can await
the same activity promise. Whenever the queue state changes making queued elements available we will
call `notifyActivity` to complete the promise. The unblocked consumers are in a FIFO priority and will execute in order
until the queue is empty, initiating another blocked state.

```scala
private def notifyActivity: UIO[Unit] =
  for {
    resetPromise <- Promise.make[Nothing, Unit]
    _ <- promise.succeed(())
  } yield promise = resetPromise
```

The `notifyActivity` method will complete the current promise, and replace it with a new uncompleted promise. All calls
to `notifyActivity` are within the `semaphore` write permit so access is guaranteed to be exclusive to the fiber,
allowing this to be a simple `var` instead of a `Ref`.

## Queue Write Operations

The queue can be modified in 3 ways: elements added, elements removed, and elements undergoing status change. These map
to `add`/`addAll` calls and `Scope` creation / finalization. Exclusive access to the queue is enforced by reserving all
permits via `semaphore.withPermits(MaxReadFibers)`. This call will block until any outstanding permits reserved by
read-only operations have been returned, and will block any new read-only permits from being obtained.

```scala
def add(elem: A):
//- obtain permits to LinkedHashSet
//- try to add to queue
//- if added successfully call notifyActivity
//- release permits

def addAll(elems: Seq[A]):
//- obtain permits to LinkedHashSet
//- try to add all to queue
//- if any added successfully call notifyActivity
//- release permits

def takeUpToNQueued(max: Int):
//- obtain permits to LinkedHashSet
//- return items with Scope
//- release permits
//- Scope finalizer:
//    - obtain permits to LinkedHashSet
//    - returns items to queue on exception
//    - calls notifyActivity
//    - release permits
//  or
//    - obtain permits to LinkedHashSet
//    - remove items from queue
//    - calls notifyActivity
//    - release permits
```

Implementation details of `add` / `addAll` are straight-forward queue enqueue operations. The return values of these
may be immaterial for many use-cases. The example code emits enqueue outcomes to the server➤client stream, but for
network efficiency these can be omitted.

The `takeUpToNQueue` implementation is best examined in 3 parts:
- taking from the queue
- handling zero queue elements
- creating a scope with finalizer


# Creating Scope using acquireReleaseExit

The `Scope` is a _trait_ and not typically defined as a named class. It should normally be anonymously constructed using
one of the `acquireRelease` methods. There are variations, the simplest being `acquireRelease` where the _acquire_ is
an action to get an `A` and _release_ is an action to perform on `A` to close it.

This queue will require the more advanced `acquireReleaseExit` method, which has the same _acquire_ but
the `A` as well as `Exit` are available to the _release_ as a tuple. ZIO `Exit` is the functional equivalent of the
Scala `Try`, resolving into either a `Success` or `Failure`. Failures can be the result of either exceptions or
interruptions.

## Scope Interruption

The other `Scope` creators `acquireReleaseInterruptible` and `acquireReleaseInterruptibleExit` need to be avoided here.
They lack the ability to determine the queue elements which were part of the scope being closed. This ability is 
critical in the correct operation of this queue (without very advanced logic being added). Because of this reason, the 
`takeUpToNQueue` has been broken into 3 stages:


### Taking N from Queue

//TODO:

### Handling Zero Queue Elements

This is separated

### Creating Scope with Finalizer

This is covered in the [Creating Scope using acquireReleaseExit](#creating-scope-using-acquirereleaseexit) section
below.




## Defining Scope acquire and release Finalizer

```scala
def acquire: ZIO[?, ?, Seq]
//- obtain permits to LinkedHashSet
//- iterate elements in queue collecting up to N unflagged
//- flag elements as being taken
//- if any elements unflagged call notifyActivity
//- release permits
```

A line-by-line implementation of the Scope _acquire_ would be:

```scala
semaphore.withPermits(MaxReadFibers) {
  val flagged = linkedHashSet.iterator
    .filter(_.status == Status.Queued)
    .take(max)
    .map {
      jobStatus =>
        jobStatus.status = Status.InProgress
        jobStatus.a
    }
  if (flagged.nonEmpty) notifyActivity.as(flagged) else ZIO.unit
}
```

The finalizer will be outcome dependent, with a path for successful Scope closure and one for failure.

```scala
def release: (Seq, Exit[Any, Any]) => ZIO[?, Nothing, Any] = {
  //if Seq has elements from the Queue:
  //- obtain permits to LinkedHashSet
  //- loop through all elements in Seq
  //- if Exit was success:
  //  - remove element from Queue
  //- if Exit was failure:
  //  - unflag element as taken in Queue
  //- if any elements unflagged call notifyActivity
  //- release permits
}
```

A line-by-line implementation of the Scope _release_ finalizer would be:

```scala
(takeUpToNOption, exit) =>
  takeUpToNOption.fold(false)(seq =>
    semaphore.withPermits(MaxReadFibers) {
      val activity = seq.foldLeft(false)((hasActivity, a) =>
        exit match {
          case Exit.Success(_) =>
            val _ = linkedHashSet.remove(JobStatus(a, Status.InProgress))
            hasActivity
          case Exit.Failure(_) =>
            val _ = linkedHashSet.find(_.a == a).foreach(_.status = Status.Queued)
            true
        })
      if (activity) notifyActivity else ZIO.unit
    }
  )
```

## Closing Scope

The mechanism to close the scope will depend on the actions performed by the consumer. The `Scope` is part of the ZIO
effect's _environment_ and appears in the type signature until it is removed. Explicitly defining a scope boundary can
be done using the `ZIO.scope` partial function. This is a closure around effect code, and drops it from the
_environment_ type:

```scala
ZIO.scope(zio: ZIO[Scope, ?, ?]): ZIO[?, ?, ?]
```

### Streaming Consumers

Typical consumers would adopt a stream pattern. As the queue releases `NonEmptyChunk` elements within a `Scope`,
consumers should opt to complete the scope as soon as possible, however long-running scope have very minimal impact on
the queue as it is already optimized minimal memory consumption and internal iteration performance.

```scala
val consumer: ZStream[Any, Throwable, T] = ZStream.repeatZIO {
  ZIO.scoped {
    queue.takeUpToNQueued(?).map {
      nonEmptyChunk => ??? //function to create Ts
    }
  }
}
```

//TODO:

# Element Uniqueness using equals / hashCode

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

## Feature Extensions using JobStatus Fields

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

# Queue Class and Private Members

There are 3 private variables in the queue (these are constructor params to avoid `Unsafe` construction). They are all
intended to be internal references only.

```scala
class UniqueJobQueue[A](
                         private val semaphore: Semaphore,
                         private var promise: Promise[Nothing, Unit],
                       ) {
  val linkedHashSet = new mutable.LinkedHashSet[JobStatus[A]]
}
```
The `semaphore` will be the read/write synchronization for the `linkedHashSet`.  The `promise` will be used to signal 
consumers to retry the queue because elements have been added. The `promise` is a mutable _var_ but will only be 
modified behind the `semaphore` write guard, ensuring no write conflicts.

# Conclusion: Putting It All Together

The example code utilizes type alias, such as `type URIO[-R, +A] = ZIO[R, Nothing, A]`, as well as using
`Chunk` and `NonEmptyChunk` instead of `Seq`.

```scala
class SynchronizedUniqueJobQueue[A](
                         private val semaphore: Semaphore,
                         private var promise: Promise[Nothing, Unit],
                       ) {
  
  private val linkedHashSet = new mutable.LinkedHashSet[JobStatus[A]]

  def add(elem: A): ZIO[Any, Nothing, Boolean]

  def addAll(elems: Seq[A]): ZIO[Any, Nothing, Seq[A]]

  def takeUpToNQueued(max: Int): ZIO[Scope, Nothing, Seq[A]]
  
  private def takeUpToQueuedAllowEmpty(max: Int): ZIO[Scope, Nothing, Option[Seq[A]]]

  private def notifyActivity: ZIO[Any, Nothing, Unit]
}
```


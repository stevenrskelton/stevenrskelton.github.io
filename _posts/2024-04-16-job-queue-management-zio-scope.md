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
for Functional Programming and their Effect Systems, such as ZIO. Effect systems are congruent to the
enterprise job queue, with ZIO fibers performing work and ZIO [Resource Management](https://zio.dev/reference/resource/)
forming the scheduling and supervision backbone. An efficient job queue can be written using ZIO constructs using
surprisingly minimal amount of code.

{% include table-of-contents.html height="100px" %}

# ZIO Resources and Scope

ZIO Resources form a strong mechanism preventing resource leaks and ensuring proper finalization and closure. A simple
job queue which maintains observability of in-progress jobs has the same concern: how can external workers be properly
accounted for within the queue.

The approach here is to allow a queue to release queued objects within
a [Scope](https://zio.dev/reference/resource/scope/), like they were a file handle or database connection, and using
this well-defined mechanism finally remove the object from the queue after the Scope closure.

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
class DistinctZioJobQueue[A]:

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

```

### Blocking in ZIO

The primary mechanism to block ZIO fibers is by mapping from `await` on a `Promise`. A second fiber should be used to
complete the promise using `succeed`. Within our queue, all consumers can await the same activity promise. Whenever
the queue state changes making queued elements available we will call `triggerActivity` to complete the promise.

```scala
/**
 * Signal all consumers to check for queued elements
 */
private def triggerActivity: UIO[Unit] =
  for {
    resetPromise <- Promise.make[Nothing, Unit]
    oldPromise <- activityRef.getAndSet(resetPromise)
    _ <- oldPromise.succeed(())
  } yield ()
```

The `triggerActivity` method will atomically complete the current promise, and replace it with a new uncompleted
promise. It is important to replace the `Ref` before completing the promise to avoid another thread from requesting
the same completed promise from the ref. An unbounded cycle would be possible if another fiber is triggered by the
promise completion, finds no elements, then awaits on the completed promise a second time. This cycle could continue
until the ref is replaced, which if the fiber never relinquishes control could be infinite.

The queue can contain new queued elements whenever items are added, or when they are made available from a scope
closing unsuccessfully.

```scala
def add(elem: A):
//- try to add to queue
//- if added successfully call triggerActivity

def addAll(elems: Seq[A]):
//- try to add all to queue
//- if any added successfully call triggerActivity

def takeUpToNQueued(max: Int):
//- return items with Scope
//- Scope finalizer returns items to queue on exception then calls triggerActivity
```

#### Creating Scope using acquireReleaseExit



### Job Uniqueness using qquals / hashCode

```scala
private enum Status:
  case Queued, InProgress

private class JobStatus[A](val a: A, var status: Status):
  override def hashCode(): Int = a.hashCode()

  override def equals(obj: Any): Boolean = obj match
    case o: JobStatus[?] => o.a.equals(a)
    case _ => false
```


### Queue Class and Private Members

```scala
class UniqueJobQueue[A](
  linkedHashSetRef: Ref[mutable.LinkedHashSet[JobStatus[A]]],
  activityRef: Ref[Promise[Nothing, Unit]]
)
```


//TODO:

{%
include figure image_path="/assets/images/2024/04/unique_job_queue.svg"
caption="Job queue using ListHashSet and ZIO Scopes to manage queue removal"
img_style="padding: 10px; background-color: white; height: 320px;"
%}
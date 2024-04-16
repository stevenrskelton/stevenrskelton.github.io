---
title: "Job Queue Management using ZIO Scopes"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
examples:
  - job-queue-zio-scope
examples-sources:
  - /src/main/scala/ca/stevenskelton/examples/jobqueuezioscope/DistinctZioJobQueue.scala
  - /src/test/scala/ca/stevenskelton/examples/jobqueuezioscope/DistinctZioJobQueueSpec.scala
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
This allows work in-progress to count towards the item uniqueness. Re-adding work that is already in-progress will be rejected by the queue.
- Popping from the queue is a blocking operation; there is no need to poll the queue for new items, consumers can stream items and fetch batches using thread-safe operations.

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
  
  //Blocks until returning a queued job.
  def takeQueued[E]: ZIO[Scope, Nothing, A]
  
  //Blocks until returning at least one, but no more than N, queued jobs.
  def takeUpToNQueued(max: Int): ZIO[Scope, Nothing, Seq[A]]
}
```
//TODO:

{%
include figure image_path="/assets/images/2024/04/distinct_job_queue.svg"
caption="Job queue using ListHashSet and ZIO Scopes to manage queue removal"
img_style="padding: 10px; background-color: white; height: 320px;"
%}
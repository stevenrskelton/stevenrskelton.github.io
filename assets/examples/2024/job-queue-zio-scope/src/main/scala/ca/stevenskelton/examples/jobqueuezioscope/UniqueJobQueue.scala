package ca.stevenskelton.examples.jobqueuezioscope

import ca.stevenskelton.examples.jobqueuezioscope.UniqueJobQueue.{JobStatus, Status}
import zio.{Chunk, Exit, NonEmptyChunk, Promise, RIO, Ref, Scope, UIO, Unsafe, ZIO}

import scala.collection.mutable

object UniqueJobQueue:

  private enum Status:
    case Queued, InProgress

  private class JobStatus[A](val a: A, var status: Status):
    override def hashCode(): Int = a.hashCode()

    override def equals(obj: Any): Boolean = obj match
      case o: JobStatus[?] => o.a.equals(a)
      case _ => false

  def create[A]: UIO[UniqueJobQueue[A]] =
    for
      linkedHashSetRef <- Ref.make(new mutable.LinkedHashSet[JobStatus[A]])
      promise <- Promise.make[Nothing, Unit]
      promiseRef <- Ref.make(promise)
    yield
      UniqueJobQueue(linkedHashSetRef, promiseRef)


class UniqueJobQueue[A] private(
                                      private val linkedHashSetRef: Ref[mutable.LinkedHashSet[JobStatus[A]]],
                                      private val activityRef: Ref[Promise[Nothing, Unit]]
                                    ):

  /**
   * Jobs waiting in queue for execution.
   */
  def queued: UIO[Chunk[A]] = linkedHashSetRef.get.map:
    linkedHashSet =>
      Chunk.from:
        linkedHashSet.iterator.withFilter(_.status == Status.Queued).map(_.a)

  /**
   * Jobs in queue that are currently executing.
   */
  def inProgress: UIO[Chunk[A]] = linkedHashSetRef.get.map:
    linkedHashSet =>
      Chunk.from:
        linkedHashSet.iterator.withFilter(_.status == Status.InProgress).map(_.a)

  /**
   * Add job to queue, will return true if successful. Jobs already in queue will return false.
   */
  def add(elem: A): UIO[Boolean] = linkedHashSetRef.get
    .map:
      _.add(JobStatus(elem, Status.Queued))
    .tap:
      added => if added then activityRef.get.map(_.succeed(())) else ZIO.unit

  /**
   * Add jobs to queue. Will return all jobs that failed to be added.
   */
  def addAll(elems: Seq[A]): UIO[Seq[A]] =
    linkedHashSetRef.get.map:
      linkedHashSet =>
        elems.foldLeft(Nil):
          (rejected, a) => if linkedHashSet.add(JobStatus(a, Status.Queued)) then rejected else rejected :+ a
    .tap:
      allRejected => if allRejected.size < elems.size then activityRef.get.map(_.succeed(())) else ZIO.unit
  
  /**
   * Non-interruptible creator of scope
   */
  private def takeUpToQueuedAllowEmpty(max: Int): RIO[Scope, Chunk[A]] = ZIO.acquireReleaseExit(
    for
      linkedHashSet <- linkedHashSetRef.get
    yield
      Chunk.from:
        linkedHashSet.iterator
          .filter(_.status == Status.Queued)
          .take(max)
          .map:
            jobStatus =>
              jobStatus.status = Status.InProgress
              jobStatus.a
  )((taken, exit) =>
    for
      linkedHashSet <- linkedHashSetRef.get
    yield
      taken.foreach:
        a =>
          exit match
            case Exit.Success(_) =>
              linkedHashSet.remove(JobStatus(a, Status.InProgress))
            case Exit.Failure(_) =>
              linkedHashSet.find(_.a == a).foreach(_.status = Status.Queued)
  )

  /**
   * Blocks until returns at least one, but no more than N, queued jobs.
   */
  def takeUpToNQueued(max: Int): RIO[Scope, NonEmptyChunk[A]] =
    takeUpToQueuedAllowEmpty(max).flatMap:
      chunk =>
        if chunk.isEmpty then activityRef.get.flatMap(_.await.flatMap(_ => takeUpToNQueued(max)))
        else Promise.make[Nothing, Unit].flatMap:
          reset =>
            activityRef.modify:
              promise =>
                val _ = Unsafe.unsafe(implicit unsafe => zio.Runtime.default.unsafe.run(promise.succeed(())))
                (NonEmptyChunk.fromChunk(chunk).get, reset)

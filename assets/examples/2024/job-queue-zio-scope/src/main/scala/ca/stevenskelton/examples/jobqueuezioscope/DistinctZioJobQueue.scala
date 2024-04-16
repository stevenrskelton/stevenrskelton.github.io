package ca.stevenskelton.examples.jobqueuezioscope

import ca.stevenskelton.examples.jobqueuezioscope.DistinctZioJobQueue.{JobStatus, Status}
import zio.{Chunk, Exit, Promise, RIO, Ref, Scope, UIO, Unsafe, ZIO}

import scala.collection.mutable

object DistinctZioJobQueue:

  private enum Status:
    case Queued, InProgress

  private class JobStatus[A](val a: A, var status: Status):
    override def hashCode(): Int = a.hashCode()

    override def equals(obj: Any): Boolean = obj match
      case o: JobStatus[?] => o.a.equals(a)
      case _ => false

  def create[A]: UIO[DistinctZioJobQueue[A]] =
    for
      linkedHashSetRef <- Ref.make(new mutable.LinkedHashSet[JobStatus[A]])
      promise <- Promise.make[Nothing, Unit]
      promiseRef <- Ref.make(promise)
    yield
      DistinctZioJobQueue(linkedHashSetRef, promiseRef)


class DistinctZioJobQueue[A] private(
                                      private val linkedHashSetRef: Ref[mutable.LinkedHashSet[JobStatus[A]]],
                                      private val activityRef: Ref[Promise[Nothing, Unit]]
                                    ):

  /**
   * Jobs waiting in queue for execution.
   */
  def queued: UIO[Seq[A]] = linkedHashSetRef.get.map(_.iterator.withFilter(_.status == Status.Queued).map(_.a).toSeq)

  /**
   * Jobs in queue that are currently executing.
   */
  def inProgress: UIO[Seq[A]] = linkedHashSetRef.get.map(_.iterator.withFilter(_.status == Status.InProgress).map(_.a).toSeq)

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
   * Blocks until returning a queued job.
   */
  def takeQueued[E]: RIO[Scope, A] = takeUpToNQueued(1).map(_.head)

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
  def takeUpToNQueued(max: Int): RIO[Scope, Chunk[A]] =
    takeUpToQueuedAllowEmpty(max).flatMap:
      chunk =>
        if chunk.isEmpty then activityRef.get.flatMap(_.await.flatMap(_ => takeUpToNQueued(max)))
        else Promise.make[Nothing, Unit].flatMap:
          reset =>
            activityRef.modify:
              promise =>
                val _ = Unsafe.unsafe(implicit unsafe => zio.Runtime.default.unsafe.run(promise.succeed(())))
                (chunk, reset)

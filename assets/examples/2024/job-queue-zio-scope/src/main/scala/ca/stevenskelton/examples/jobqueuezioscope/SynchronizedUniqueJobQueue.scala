package ca.stevenskelton.examples.jobqueuezioscope

import ca.stevenskelton.examples.jobqueuezioscope.SynchronizedUniqueJobQueue.{JobStatus, Status}
import zio.{Chunk, Exit, NonEmptyChunk, Promise, RIO, Ref, Scope, Semaphore, UIO, ZIO}

import scala.collection.mutable

object SynchronizedUniqueJobQueue:

  private enum Status:
    case Queued, InProgress

  private class JobStatus[A](val a: A, var status: Status):
    override def hashCode(): Int = a.hashCode()

    override def equals(obj: Any): Boolean = obj match
      case o: JobStatus[?] => o.a.equals(a)
      case _ => false

  def create[A]: UIO[SynchronizedUniqueJobQueue[A]] =
    for
      semaphore <- Semaphore.make(1)
      promise <- Promise.make[Nothing, Unit]
      promiseRef <- Ref.make(promise)
    yield
      SynchronizedUniqueJobQueue(semaphore, promiseRef)


class SynchronizedUniqueJobQueue[A] private(
                                 private val semaphore: Semaphore,
                                 private val activityRef: Ref[Promise[Nothing, Unit]]
                               ):
  /**
   * Not thread-safe, accessed behind semaphore
   */
  private val linkedHashSet = new mutable.LinkedHashSet[JobStatus[A]]

  /**
   * Jobs waiting in queue for execution.
   */
  def queued: UIO[Chunk[A]] = semaphore.withPermit:
    ZIO.succeed:
      Chunk.from:
        linkedHashSet.iterator.withFilter(_.status == Status.Queued).map(_.a)

  /**
   * Jobs in queue that are currently executing.
   */
  def inProgress: UIO[Chunk[A]] = semaphore.withPermit:
    ZIO.succeed:
      Chunk.from:
        linkedHashSet.iterator.withFilter(_.status == Status.InProgress).map(_.a)

  /**
   * Add job to queue, will return true if successful. Jobs already in queue will return false.
   */
  def add(elem: A): UIO[Boolean] =
    semaphore.withPermit:
      ZIO.succeed:
        linkedHashSet.add(JobStatus(elem, Status.Queued))
    .tap:
      added => if added then triggerActivity else ZIO.unit

  /**
   * Add jobs to queue. Will return all jobs that failed to be added.
   */
  def addAll(elems: Seq[A]): UIO[Seq[A]] =
    semaphore.withPermit:
      ZIO.succeed:
        elems.foldLeft(Nil):
          (rejected, a) => if linkedHashSet.add(JobStatus(a, Status.Queued)) then rejected else rejected :+ a
    .tap:
      rejected => if rejected.size < elems.size then triggerActivity else ZIO.unit

  /**
   * Non-interruptible creator of scope
   */
  private def takeUpToQueuedAllowEmpty(max: Int): RIO[Scope, Chunk[A]] = ZIO.acquireReleaseExit(
    semaphore.withPermit:
      ZIO.succeed:
        Chunk.from:
          linkedHashSet.iterator
            .filter(_.status == Status.Queued)
            .take(max)
            .map:
              jobStatus =>
                jobStatus.status = Status.InProgress
                jobStatus.a
  )((taken, exit) =>
    semaphore.withPermit:
      ZIO.succeed:
        taken.foldLeft(false):
          (requeued, a) =>
            exit match
              case Exit.Success(_) =>
                val _ = linkedHashSet.remove(JobStatus(a, Status.InProgress))
                requeued
              case Exit.Failure(_) =>
                val _ = linkedHashSet.find(_.a == a).foreach(_.status = Status.Queued)
                true
    .flatMap:
      wasRequeued => if wasRequeued then triggerActivity else ZIO.unit
  )

  /**
   * Blocks until returns at least one, but no more than N, queued jobs.
   */
  def takeUpToNQueued(max: Int): RIO[Scope, NonEmptyChunk[A]] =
    takeUpToQueuedAllowEmpty(max).flatMap:
      chunk =>
        NonEmptyChunk.fromChunk(chunk)
          .map:
            nonEmptyChunk => triggerActivity.as(nonEmptyChunk)
          .getOrElse:
            activityRef.get.flatMap(_.await.flatMap(_ => takeUpToNQueued(max)))

  /**
   * Signal all consumers to check for queued elements
   */
  private def triggerActivity: UIO[Unit] =
    for
      resetPromise <- Promise.make[Nothing, Unit]
      oldPromise <- activityRef.getAndSet(resetPromise)
      _ <- oldPromise.succeed(())
    yield ()

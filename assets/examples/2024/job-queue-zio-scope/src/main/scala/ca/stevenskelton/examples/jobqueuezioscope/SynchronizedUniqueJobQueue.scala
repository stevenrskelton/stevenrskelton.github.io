package ca.stevenskelton.examples.jobqueuezioscope

import ca.stevenskelton.examples.jobqueuezioscope.SynchronizedUniqueJobQueue.{JobStatus, MaxReadFibers, Status}
import zio.{Chunk, Exit, NonEmptyChunk, Promise, RIO, Scope, Semaphore, UIO, ZIO}

import scala.collection.mutable

object SynchronizedUniqueJobQueue:

  private enum Status:
    case Queued, InProgress

  private class JobStatus[A](val a: A, var status: Status):
    override def hashCode(): Int = a.hashCode()

    override def equals(obj: Any): Boolean = obj match
      case o: JobStatus[?] => o.a.equals(a)
      case _ => false

  private val MaxReadFibers = Int.MaxValue.toLong

  def create[A]: UIO[SynchronizedUniqueJobQueue[A]] =
    for
      semaphore <- Semaphore.make(MaxReadFibers)
      promise <- Promise.make[Nothing, Unit]
    yield
      SynchronizedUniqueJobQueue(semaphore, promise)

/**
 * Allows for unique entries in the queue, using Object.hashCode
 * Entries remain in the queue while externally processed, uses ZIO Scope
 *  to determine the processing outcome.  Successfully closed Scope will
 *  remove those entries from the queue, Unsuccessfully closed Scope will
 *  release the entries back into the queue to be taken again.
 * @param semaphore Synchronization for the LinkedHashSet
 * @param promise All access to this is within the semaphore
 */
class SynchronizedUniqueJobQueue[A] private(
                                 private val semaphore: Semaphore,
                                 private var promise: Promise[Nothing, Unit],
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
  def add(elem: A): UIO[Boolean] = semaphore.withPermits(MaxReadFibers):
    ZIO.succeed:
      linkedHashSet.add(JobStatus(elem, Status.Queued))
    .tap:
      added => if added then notifyActivity else ZIO.unit

  /**
   * Add jobs to queue. Will return all jobs that failed to be added.
   */
  def addAll(elems: Seq[A]): UIO[Seq[A]] = semaphore.withPermits(MaxReadFibers):
    ZIO.succeed:
      elems.foldLeft(Nil):
        (rejected, a) => if linkedHashSet.add(JobStatus(a, Status.Queued)) then rejected else rejected :+ a
    .tap:
      rejected => if rejected.size < elems.size then notifyActivity else ZIO.unit

  /**
   * Non-interruptible creator of scope
   */
  private def takeUpToQueuedAllowEmpty(max: Int): RIO[Scope, Option[NonEmptyChunk[A]]] = ZIO.acquireReleaseExit(
    semaphore.withPermits(MaxReadFibers):
      ZIO.succeed:
        NonEmptyChunk.fromChunk:
          Chunk.from:
            linkedHashSet.iterator
              .filter(_.status == Status.Queued)
              .take(max)
              .map:
                jobStatus =>
                  jobStatus.status = Status.InProgress
                  jobStatus.a
      .tap:
        opt => if opt.isDefined then notifyActivity else ZIO.unit
  )((taken, exit) =>
    taken.map:
      chunk =>
        semaphore.withPermits(MaxReadFibers):
          val activity = chunk.foldLeft(false):
            (hasActivity, a) =>
              exit match
                case Exit.Success(_) =>
                  val _ = linkedHashSet.remove(JobStatus(a, Status.InProgress))
                  hasActivity
                case Exit.Failure(_) =>
                  val _ = linkedHashSet.find(_.a == a).foreach(_.status = Status.Queued)
                  true
          if activity then notifyActivity else ZIO.unit
    .getOrElse:
      ZIO.succeed(false)
  )

  /**
   * Blocks until returns at least one, but no more than N, queued jobs.
   */
  def takeUpToNQueued(max: Int): RIO[Scope, NonEmptyChunk[A]] =
    takeUpToQueuedAllowEmpty(max).flatMap:
      _.map(ZIO.succeed).getOrElse:
        promise.await.flatMap(_ => takeUpToNQueued(max))

  /**
   * Signal all consumers to recheck the queue
   */
  private def notifyActivity: UIO[Unit] =
    for
      resetPromise <- Promise.make[Nothing, Unit]
      _ <- promise.succeed(())
    yield
      promise = resetPromise

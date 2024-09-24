//package ca.stevenskelton.examples.jobqueuezioscope
//
//import ca.stevenskelton.examples.jobqueuezioscope.RefUniqueJobQueue.{JobStatus, Status}
//import zio.{Chunk, Exit, NonEmptyChunk, Promise, RIO, Ref, Scope, UIO, ZIO}
//
//import scala.collection.immutable.Queue
//import scala.collection.mutable
//
//object RefUniqueJobQueue:
//
//  private enum Status:
//    case Queued, InProgress
//
//  private case class JobStatus[A](a: A, status: Status):
//    override def hashCode(): Int = a.hashCode()
//
//    override def equals(obj: Any): Boolean = obj match
//      case o: JobStatus[?] => o.a.equals(a)
//      case _ => false
//
//  def create[A]: UIO[RefUniqueJobQueue[A]] =
//    for
//      queueRef <- Ref.make(Queue.empty[JobStatus[A]])
//      promise <- Promise.make[Nothing, Unit]
//      promiseRef <- Ref.make(promise)
//    yield
//      RefUniqueJobQueue(queueRef, promiseRef)
//
//
//class RefUniqueJobQueue[A] private(
//                                    private val queueRef: Ref[Queue[JobStatus[A]]],
//                                    private val activityRef: Ref[Promise[Nothing, Unit]]
//                                  ):
//
//  /**
//   * Jobs waiting in queue for execution.
//   */
//  def queued: UIO[Chunk[A]] = queueRef.get.map:
//    linkedHashSet =>
//      Chunk.from:
//        linkedHashSet.iterator.withFilter(_.status == Status.Queued).map(_.a)
//
//  /**
//   * Jobs in queue that are currently executing.
//   */
//  def inProgress: UIO[Chunk[A]] = queueRef.get.map:
//    linkedHashSet =>
//      Chunk.from:
//        linkedHashSet.iterator.withFilter(_.status == Status.InProgress).map(_.a)
//
//  /**
//   * Add job to queue, will return true if successful. Jobs already in queue will return false.
//   */
//  def add(elem: A): UIO[Boolean] =
//    queueRef.modify:
//      queue =>
//        if queue.contains(elem) then (false, queue)
//        else (true, queue.enqueue(JobStatus(elem, Status.Queued)))
//    .tap:
//      added => if added then triggerActivity else ZIO.unit
//
//  /**
//   * Add jobs to queue. Will return all jobs that failed to be added.
//   */
//  def addAll(elems: Seq[A]): UIO[Seq[A]] =
//    queueRef.modify:
//      queue =>
//        elems.foldLeft((Nil, queue)):
//          case ((rejected, q), a) =>
//            if q.contains(a) then (rejected :+ a, q)
//            else (rejected, q.enqueue(JobStatus(a, Status.Queued)))
//    .tap:
//      rejected => if rejected.size < elems.size then triggerActivity else ZIO.unit
//
//  /**
//   * Non-interruptible creator of scope
//   */
//  private def takeUpToQueuedAllowEmpty(max: Int): RIO[Scope, Chunk[A]] = ZIO.acquireReleaseExit(
//    queueRef.modify:
//      queue =>
//        val (q, seqA) = queue.foldLeft((Queue.empty[JobStatus[A]], List.empty[A])):
//          case ((newQueue, inProgress), jobStatus) =>
//            if newQueue.size < max && jobStatus.status == Status.Queued then
//              (newQueue.enqueue(jobStatus.copy(status = Status.InProgress)), inProgress :+ jobStatus.a)
//            else (newQueue.enqueue(jobStatus), inProgress)
//        (Chunk.from(seqA), q)
//  )((taken, exit) =>
//    queueRef.modify:
//      queue =>
//        taken.map:
//          a =>
//            exit match
//              case Exit.Success(_) =>
//                val _ = linkedHashSet.remove(JobStatus(a, Status.InProgress))
//                false
//              case Exit.Failure(_) =>
//                val _ = linkedHashSet.find(_.a == a).foreach(_.status = Status.Queued)
//                true
//    .flatMap:
//      wasRequeued => if wasRequeued.exists(identity) then triggerActivity else ZIO.unit
//  )
//
//  /**
//   * Blocks until returns at least one, but no more than N, queued jobs.
//   */
//  def takeUpToNQueued(max: Int): RIO[Scope, NonEmptyChunk[A]] =
//    takeUpToQueuedAllowEmpty(max).flatMap:
//      chunk =>
//        NonEmptyChunk.fromChunk(chunk)
//          .map:
//            nonEmptyChunk => triggerActivity.as(nonEmptyChunk)
//          .getOrElse:
//            activityRef.get.flatMap(_.await.flatMap(_ => takeUpToNQueued(max)))
//
//  /**
//   * Signal all consumers to check for queued elements
//   */
//  private def triggerActivity: UIO[Unit] =
//    for
//      resetPromise <- Promise.make[Nothing, Unit]
//      oldPromise <- activityRef.getAndSet(resetPromise)
//      _ <- oldPromise.succeed(())
//    yield ()

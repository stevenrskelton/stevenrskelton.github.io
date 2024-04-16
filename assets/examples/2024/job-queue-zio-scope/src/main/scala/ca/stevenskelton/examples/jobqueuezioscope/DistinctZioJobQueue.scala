package ca.stevenskelton.examples.jobqueuezioscope

import ca.stevenskelton.examples.jobqueuezioscope.DistinctZioJobQueue.{JobStatus, Status}
import zio.{Chunk, Promise, Ref, Scope, UIO, ZIO}

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

  def queued: UIO[Seq[A]] = linkedHashSetRef.get.map(_.iterator.withFilter(_.status == Status.Queued).map(_.a).toSeq)

  def inProgress: UIO[Seq[A]] = linkedHashSetRef.get.map(_.iterator.withFilter(_.status == Status.InProgress).map(_.a).toSeq)

  def add(elem: A): UIO[Boolean] = linkedHashSetRef.get
    .map:
      _.add(JobStatus(elem, Status.Queued))
    .tap:
      added => activityRef.get.map(_.succeed(()))

  def addAll(elems: Seq[A]): UIO[Seq[A]] =
    linkedHashSetRef.get.map:
      linkedHashSet =>
        elems.foldLeft(Nil):
          (rejected, a) => if (linkedHashSet.add(JobStatus(a, Status.Queued))) rejected else rejected :+ a
    .tap:
      allRejected => if (allRejected.size < elems.size) activityRef.get.map(_.succeed(())) else ZIO.unit

  def takeQueued[E]: ZIO[Scope, E, A] = takeUpToNQueued(1).map(_.head)

  private def takeUpToQueuedAllowEmpty(max: Int): ZIO[Scope, Nothing, Chunk[A]] = ZIO.acquireRelease(
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
  )(taken =>
    for
      linkedHashSet <- linkedHashSetRef.get
    yield
      taken.foreach:
        a => linkedHashSet.remove(JobStatus(a, Status.Queued))
  )

  def takeUpToNQueued(max: Int): ZIO[Scope, Nothing, Chunk[A]] =
    takeUpToQueuedAllowEmpty(max).flatMap:
      chunk =>
        if (chunk.isEmpty) activityRef.get.flatMap(_.await.flatMap(_ => takeUpToNQueued(max)))
        else Promise.make[Nothing, Unit].flatMap:
          reset =>
            activityRef.modify:
              promise =>
                promise.succeed(())
                (chunk, reset)





package ca.stevenskelton.examples.distinctjobqueue

import ca.stevenskelton.examples.distinctjobqueue.DistinctZioJobQueue.{JobStatus, Status}
import zio.{Ref, Scope, UIO, ZIO}

import scala.collection.mutable

object DistinctZioJobQueue:

  private enum Status:
    case Queued, InProgress

  private class JobStatus[A](val a: A, var status: Status):
    override def hashCode(): Int = a.hashCode()

    override def equals(obj: Any): Boolean = obj match
      case o: JobStatus[?] => o.a.equals(a)
      case _ => false

  def create[A]: UIO[DistinctZioJobQueue[A]] = Ref.make(new mutable.LinkedHashSet[JobStatus[A]]).map(DistinctZioJobQueue(_))


class DistinctZioJobQueue[A] private(
                                      private val linkedHashSetRef: Ref[mutable.LinkedHashSet[JobStatus[A]]]
                                    ):

  def queued: UIO[Seq[A]] = linkedHashSetRef.get.map(_.iterator.withFilter(_.status == Status.Queued).map(_.a).toSeq)

  def inProgress: UIO[Seq[A]] = linkedHashSetRef.get.map(_.iterator.withFilter(_.status == Status.InProgress).map(_.a).toSeq)

  def add(elem: A): UIO[Boolean] = linkedHashSetRef.get.map(_.add(JobStatus(elem, Status.Queued)))

  def addAll(xs: IterableOnce[A]): UIO[Seq[A]] =
    for
      linkedHashSet <- linkedHashSetRef.get
    yield
      xs.iterator.foldLeft(Nil):
        (rejected, a) => if (linkedHashSet.add(JobStatus(a, Status.Queued))) rejected else rejected :+ a

  def takeQueued[E]: ZIO[Scope, E, Option[A]] = takeUpToQueued(1).map(_.headOption)

  def takeUpToQueued[E](max: Int): ZIO[Scope, E, Seq[A]] = ZIO.acquireRelease(
    for
      linkedHashSet <- linkedHashSetRef.get
    yield
      linkedHashSet.iterator
        .filter(_.status == Status.Queued)
        .take(max)
        .map:
          jobStatus =>
            jobStatus.status = Status.InProgress
            jobStatus.a
        .toSeq
  )(taken =>
    for
      linkedHashSet <- linkedHashSetRef.get
    yield
      taken.foreach:
        a => linkedHashSet.remove(JobStatus(a, Status.Queued))
  )







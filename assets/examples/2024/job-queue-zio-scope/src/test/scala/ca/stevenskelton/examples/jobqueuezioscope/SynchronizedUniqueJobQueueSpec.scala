package ca.stevenskelton.examples.jobqueuezioscope

import zio.stream.ZStream
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{Chunk, Promise, Scope, ZIO}

class SynchronizedUniqueJobQueueSpec extends JUnitRunnableSpec:
  override def spec = SynchronizedUniqueJobQueueSpec.spec

object SynchronizedUniqueJobQueueSpec extends JUnitRunnableSpec {

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("operations")(
    test("Distinct values on add, addAll") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        noConflictAddAll0 <- queue.addAll(Seq(1, 5))
        noConflictAddAll1 <- queue.addAll(Seq(2, 6))
        noConflictAdd <- queue.add(4)
        conflictAdd <- queue.add(5)
        conflictAddAll <- queue.addAll(Seq(3, 4, 5))
        queued <- queue.queued
      yield assertTrue:
        noConflictAddAll0.isEmpty &&
          noConflictAddAll1.isEmpty &&
          noConflictAdd &&
          !conflictAdd &&
          conflictAddAll == Seq(4, 5) &&
          queued == Seq(1, 5, 2, 6, 4, 3)
    },
    test("Remove on Scope success") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        _ <- queue.addAll(Seq(1, 5, 2, 6, 4, 3))
        queued0 <- queue.queued
        inprogress0 <- queue.inProgress
        take0 <- ZIO.scoped {
          for
            inprogressBefore <- queue.inProgress
            takeUpTo0 <- queue.takeUpToNQueued(3)
            inprogressAfter <- queue.inProgress
          yield
            (takeUpTo0.toChunk, inprogressBefore.isEmpty, takeUpTo0.toChunk == inprogressAfter)
        }
        queued1 <- queue.queued
        inprogress1 <- queue.inProgress
        take1 <- ZIO.scoped {
          for
            inprogressBefore <- queue.inProgress
            takeUpTo1 <- queue.takeUpToNQueued(1)
            inprogressAfter <- queue.inProgress
          yield
            (takeUpTo1.toChunk, inprogressBefore.isEmpty, takeUpTo1.toChunk == inprogressAfter)
        }
        queued2 <- queue.queued
        inprogress2 <- queue.inProgress
      yield assertTrue:
        queued0 == Seq(1, 5, 2, 6, 4, 3) &&
          inprogress0.isEmpty &&
          take0 == (Seq(1, 5, 2), true, true) &&
          queued1 == Seq(6, 4, 3) &&
          inprogress1.isEmpty &&
          take1 == (Seq(6), true, true) &&
          queued2 == Seq(4, 3) &&
          inprogress2.isEmpty
    },
    test("Release back on Scope failure") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        _ <- queue.addAll(Seq(1, 5, 2, 6, 4, 3))
        takeUpTo0 <- ZIO.scoped {
          for
            takeUpTo <- queue.takeUpToNQueued(3)
            _ <- ZIO.fail(new Exception(takeUpTo.map(_.toString).mkString(",")))
          yield ""
        }.catchAll {
          ex => ZIO.succeed(ex.getMessage)
        }
        queued1 <- queue.queued
        inprogress1 <- queue.inProgress
        takeUpTo1 <- ZIO.scoped(queue.takeUpToNQueued(3))
        queued2 <- queue.queued
      yield assertTrue:
        takeUpTo0 == "1,5,2" &&
          queued1 == Seq(1, 5, 2, 6, 4, 3) &&
          inprogress1.isEmpty &&
          takeUpTo1.toChunk == Seq(1, 5, 2) &&
          queued2 == Seq(6, 4, 3)
    },
    test("Release back on Scope interrupt") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        _ <- queue.addAll(Seq(1, 5, 2, 6, 4, 3))
        takenCompleted <- Promise.make[Any, Boolean]
        takeUpTo0Fiber <- ZIO.scoped {
          for
            promise <- Promise.make[Any, Unit]
            _ <- queue.takeUpToNQueued(3)
            _ <- takenCompleted.succeed(true)
            _ <- promise.await
          yield ()
        }.fork
        _ <- takenCompleted.await
        queued1 <- queue.queued
        inprogress1 <- queue.inProgress
        exit <- takeUpTo0Fiber.interrupt
        queued2 <- queue.queued
        inprogress2 <- queue.inProgress
      yield assertTrue:
        queued1 == Seq(6, 4, 3) &&
          inprogress1 == Seq(1, 5, 2) &&
          exit.isFailure &&
          queued2 == Seq(1, 5, 2, 6, 4, 3) &&
          inprogress2.isEmpty
    },
    test("Distinct values include inprogress") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        _ <- queue.addAll(Seq(1, 5, 2, 6, 4, 3))
        work <- ZIO.scoped {
          for
            taken <- queue.takeUpToNQueued(3)
            addY <- queue.add(8)
            addN <- queue.add(2)
            addAll <- queue.addAll(Seq(2, 7))
            queued <- queue.queued
            inprogress <- queue.inProgress
          yield
            (addY, addN, addAll, queued, taken, inprogress)
        }
        (innerAddY, innerAddN, innerAddAll, innerQueued, innerTaken, innerInProgress) = work
        queued <- queue.queued
        inprogress <- queue.inProgress
      yield assertTrue:
        innerAddY &&
          !innerAddN &&
          innerAddAll == Seq(2) &&
          innerQueued == Seq(6, 4, 3, 8, 7) &&
          innerTaken.toChunk == Seq(1, 5, 2) &&
          innerInProgress == Seq(1, 5, 2) &&
          queued == Seq(6, 4, 3, 8, 7) &&
          inprogress.isEmpty
    },
    test("Block on takeUpTo") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        takeUpToFork3 <- ZIO.scoped(queue.takeUpToNQueued(3)).fork
        takeUpToFork2 <- ZIO.scoped(queue.takeUpToNQueued(2)).fork
        _ <- queue.addAll(Seq(1, 5, 2, 6, 4, 3))
        takeUpTo3 <- takeUpToFork3.join
        takeUpTo2 <- takeUpToFork2.join
        queued0 <- queue.queued
      yield assertTrue:
        takeUpTo3.size == 3 &&
          takeUpTo2.size == 2 &&
          (takeUpTo3 ++ takeUpTo2).sorted == Seq(1, 2, 4, 5, 6) &&
          queued0 == Seq(3)
    },
    test("Example consumer stream") {
      for
        queue <- SynchronizedUniqueJobQueue.create[Int]
        streamOfSums = ZStream.repeatZIO {
          ZIO.scoped {
            queue.takeUpToNQueued(3).map(_.sum)
          }
        }
        _ <- queue.addAll(Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13))
        chunksOfSums <- streamOfSums.take(4).runCollect
        queued0 <- queue.queued
      yield assertTrue:
        chunksOfSums == Seq(6, 15, 24, 33) &&
          queued0 == Seq(13)
    },
  )
}

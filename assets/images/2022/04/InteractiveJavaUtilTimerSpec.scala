import java.time._
import java.util
import java.util.{ Date, Timer, TimerTask, UUID }

import org.slf4j.Logger
import play.api.test.PlaySpecification

import services.logging.TestLogger

import scala.util.control.Breaks.{ break, breakable }
import scala.util.control.NonFatal

class InteractiveJavaUtilTimerSpec extends PlaySpecification {

  var i = 0

  val timer = new InteractiveJavaUtilTimer

  def createTask1 = new NamedTask("name1") {
    override def run(logger: Logger, break: => Nothing): Unit = {
      Thread.sleep(200)
      if (shouldCancel) i = -10
      else i += 1
    }
  }

  def createTask2 = new NamedTask("name2") {
    override def run(logger: Logger, break: => Nothing): Unit = {
      Thread.sleep(100)
      i += 3
    }
  }

  def createTask3 = new NamedTask("name3") {
    override def run(logger: Logger, break: => Nothing): Unit = {
      Thread.sleep(100)
      i += 5
    }
  }

  sequential

  "abstract" should {

    "list tasks" in {
      timer.cancel()
      i = 0
      val task1 = createTask1
      val task2 = createTask2

      timer.schedule(task1)
      timer.schedule(task2)
      val prelist = timer.list(ZoneId.systemDefault())
      prelist should have size 2
      val name1 = prelist(0)
      name1.name === task1.name
      name1.isRunning === true
      name1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      val name2 = prelist(1)
      name2.name === task2.name
      name2.isRunning === false
      name2.executionStart.toInstant should beGreaterThanOrEqualTo(name1.executionStart.toInstant)
      name2.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      i === 0
      eventually(i === 1)
      timer.list(ZoneId.systemDefault()) should have size 1

      eventually(i === 4)
      timer.list(ZoneId.systemDefault()) should have size 0
    }

    "cancel tasks" in {
      timer.cancel()
      i = 0
      val task1 = createTask1
      val task2 = createTask2
      val task3 = createTask3

      timer.schedule(task1)
      val t2 = task2
      timer.schedule(t2)
      Thread.sleep(1)
      timer.schedule(task3)

      val prelist = timer.list(ZoneId.systemDefault())
      prelist should have size 3
      val name1 = prelist(0)
      name1.name === task1.name
      name1.isRunning === true
      name1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      val name2 = prelist(1)
      name2.name === task2.name
      name2.isRunning === false
      name2.executionStart.toInstant should beGreaterThanOrEqualTo(name1.executionStart.toInstant)
      name2.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      val name3 = prelist(2)
      name3.name === task3.name
      name3.isRunning === false
      name3.executionStart.toInstant should beGreaterThanOrEqualTo(name1.executionStart.toInstant)
      name3.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      i === 0
      timer.cancel(task2.uuid)
      eventually(i === 1)
      val postlist = timer.list(ZoneId.systemDefault())
      postlist should have size 1
      val postname1 = postlist(0)
      postname1.name === task3.name
      postname1.isRunning === true
      postname1.executionStart.toInstant should beGreaterThanOrEqualTo(name3.executionStart.toInstant)
      postname1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      eventually(i === 6)
      timer.list(ZoneId.systemDefault()) should have size 0
    }

    "not cancel running tasks" in {
      timer.cancel()
      i = 0
      val task1 = createTask1
      val task2 = createTask2

      timer.schedule(task1)
      timer.schedule(task2)
      val prelist = timer.list(ZoneId.systemDefault())
      prelist should have size 2
      val name1 = prelist(0)
      name1.name === task1.name
      name1.isRunning === true
      name1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      val name2 = prelist(1)
      name2.name === task2.name
      name2.isRunning === false
      name2.executionStart.toInstant should beGreaterThanOrEqualTo(name1.executionStart.toInstant)
      name2.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      i === 0
      timer.cancel(task1.uuid)

      val postlist = timer.list(ZoneId.systemDefault())
      postlist should have size 2
      val postName1 = postlist(0)
      postName1.name === task1.name
      postName1.isRunning === true
      postName1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      val postName2 = postlist(1)
      postName2.name === task2.name
      postName2.isRunning === false
      postName2.executionStart.toInstant should beGreaterThanOrEqualTo(name1.executionStart.toInstant)
      postName2.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      eventually(i === -10)
      timer.list(ZoneId.systemDefault()) should have size 1

      eventually(i === -7)
      timer.list(ZoneId.systemDefault()) should have size 0
    }

    "cancel all shouldn't cancel running" in {
      timer.cancel()
      i = 0
      val task1 = createTask1
      val task2 = createTask2

      timer.schedule(task1)
      timer.schedule(task2)
      val prelist = timer.list(ZoneId.systemDefault())
      prelist should have size 2
      val name1 = prelist(0)
      name1.name === task1.name
      name1.isRunning === true
      name1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      val name2 = prelist(1)
      name2.name === task2.name
      name2.isRunning === false
      name2.executionStart.toInstant should beGreaterThanOrEqualTo(name1.executionStart.toInstant)
      name2.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      i === 0
      timer.cancel()

      val postlist = timer.list(ZoneId.systemDefault())
      postlist should have size 1
      val postName1 = postlist(0)
      postName1.name === task1.name
      postName1.isRunning === true
      postName1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      eventually(i === -10)
      timer.list(ZoneId.systemDefault()) should have size 0
    }
  }

  "time" should {
    "set future time, schedule in order" in {
      timer.cancel()
      i = 0
      val task1 = createTask1
      val task1time = ZonedDateTime.now().plusDays(1)
      val task2 = createTask2
      val task2time = ZonedDateTime.now().plusMinutes(5)

      timer.schedule(task1, task1time)
      timer.schedule(task2, task2time)
      val prelist = timer.list(ZoneId.systemDefault())
      prelist should have size 2
      val name1 = prelist(0)
      name1.name === task2.name
      name1.isRunning === false
      name1.executionStart should beEqualTo(task2time)

      val name2 = prelist(1)
      name2.name === task1.name
      name2.isRunning === false
      name2.executionStart should beEqualTo(task1time)

      i === 0
      timer.cancel()
      timer.list(ZoneId.systemDefault()) should have size 0
    }
    "adjust zones" in {
      timer.cancel()
      i = 0
      val task1 = createTask1
      val task1time = ZonedDateTime.now(ZoneId.ofOffset("", ZoneOffset.ofHours(1))).plusHours(3)
      val task2 = createTask2
      val task2time = ZonedDateTime.now(ZoneId.ofOffset("", ZoneOffset.ofHours(9))).plusHours(5)

      timer.schedule(task1, task1time)
      timer.schedule(task2, task2time)
      val preZoneId = ZoneId.systemDefault()
      val prelist = timer.list(preZoneId)
      prelist should have size 2
      val name1 = prelist(0)
      name1.name === task1.name
      name1.isRunning === false
      name1.executionStart.toInstant should beEqualTo(task1time.toInstant)
      name1.executionStart.getZone === preZoneId

      val name2 = prelist(1)
      name2.name === task2.name
      name2.isRunning === false
      name2.executionStart.toInstant should beEqualTo(task2time.toInstant)
      name2.executionStart.getZone === preZoneId

      val postZoneId = ZoneOffset.ofHours(9)
      val postlist = timer.list(ZoneId.ofOffset("", postZoneId))
      postlist should have size 2
      val postName1 = postlist(0)
      postName1.name === task1.name
      postName1.isRunning === false
      postName1.executionStart.toInstant should beEqualTo(task1time.toInstant)
      postName1.executionStart.getZone === postZoneId

      val postName2 = postlist(1)
      postName2.name === task2.name
      postName2.isRunning === false
      postName2.executionStart.toInstant should beEqualTo(task2time.toInstant)
      postName2.executionStart.getZone === postZoneId
    }
  }

  "break" should {
    "exit task but allow more tasks to be scheduled" in {
      timer.cancel()
      i = 0
      val task1 = new NamedTask("name1") {
        override def run(logger: Logger, break: => Nothing): Unit = {
          (0 to 10).foreach { _ =>
            if (shouldCancel) break
            else Thread.sleep(20)
          }
          i += 1
        }
      }
      val task2 = new NamedTask("name2") {
        override def run(logger: Logger, break: => Nothing): Unit = {
          (0 to 10).foreach { _ =>
            if (shouldCancel) break
            else Thread.sleep(20)
          }
          i += 3
        }
      }

      timer.schedule(task1)
      timer.schedule(task2)
      val prelist = timer.list(ZoneId.systemDefault())
      prelist should have size 2

      i === 0
      timer.cancel(task1.uuid)
      Thread.sleep(100)
      i === 0

      val postlist = timer.list(ZoneId.systemDefault())
      postlist should have size 1
      val name1 = postlist(0)
      name1.name === task2.name
      name1.isRunning === true
      name1.executionStart.toInstant should beLessThan(ZonedDateTime.now().toInstant)

      eventually(i === 3)
      timer.list(ZoneId.systemDefault()) should have size 0
    }
  }
}

import java.time._
import java.util
import java.util.{ Date, Timer, TimerTask, UUID }

import org.slf4j.Logger
import play.api.test.PlaySpecification

import services.logging.TestLogger

import scala.util.control.Breaks.{ break, breakable }
import scala.util.control.NonFatal

abstract class NamedTask(val name: String) {
  val uuid = UUID.randomUUID()

  var shouldCancel: Boolean = false

  def run(logger: Logger, break: => Nothing): Unit
}

class InteractiveJavaUtilTimer {

  def createLogger(name: String, uuid: UUID): Logger = TestLogger.create.logger

  def humanReadableFormat(duration: Duration): String = {
    duration.toString.substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase
  }

  def humanReadableTimeFromStart(starttime: Long): String = humanReadableFormat(Duration.ofMillis(System.currentTimeMillis - starttime))

  private class NamedTimerTask(val namedTask: NamedTask) extends TimerTask {

    var (isRunning, isComplete, hasFailed) = (false, false, false)

    override def cancel: Boolean = {
      namedTask.shouldCancel = true
      super.cancel()
    }

    override def run: Unit = if (!namedTask.shouldCancel) {
      isRunning = true
      val logger = createLogger(namedTask.name, namedTask.uuid)
      val starttime = System.currentTimeMillis
      try {
        logger.info(s"Job started.")
        breakable {
          namedTask.run(logger, break)
          isComplete = true
        }
        if (isComplete) logger.info(s"Job complete after ${humanReadableTimeFromStart(starttime)}")
        else {
          hasFailed = true
          logger.info(s"Job cancelled after ${humanReadableTimeFromStart(starttime)}")
        }
      } catch {
        case NonFatal(ex) =>
          hasFailed = true
          logger.error(s"Job failed after ${humanReadableTimeFromStart(starttime)}", ex)
      } finally {
        isRunning = false
        allTasks.remove(this)
      }
    }
  }

  case class AdvancedSchedulingTimerTasks(name: String, uuid: UUID, executionStart: ZonedDateTime, isRunning: Boolean)

  private val timer = new Timer(true)

  private val allTasks = java.util.Collections.newSetFromMap[NamedTimerTask](new util.WeakHashMap[NamedTimerTask, java.lang.Boolean]())

  def schedule(namedTask: NamedTask): Boolean = {
    val namedTimerTask = new NamedTimerTask(namedTask)
    allTasks.add(namedTimerTask)
    timer.schedule(namedTimerTask, 0)
    namedTimerTask.isRunning
  }

  def schedule(namedTask: NamedTask, time: ZonedDateTime): Boolean = {
    val namedTimerTask = new NamedTimerTask(namedTask)
    allTasks.add(namedTimerTask)
    timer.schedule(namedTimerTask, Date.from(time.toInstant))
    namedTimerTask.isRunning
  }

  def list(zoneId: ZoneId): Seq[AdvancedSchedulingTimerTasks] = {
    allTasks.toArray.map(_.asInstanceOf[NamedTimerTask]).flatMap {
      namedTimerTask =>
        val isCancelled = namedTimerTask.namedTask.shouldCancel && !namedTimerTask.isRunning
        if (isCancelled || namedTimerTask.isComplete || namedTimerTask.hasFailed) None
        else Some {
          AdvancedSchedulingTimerTasks(
            namedTimerTask.namedTask.name,
            namedTimerTask.namedTask.uuid,
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(namedTimerTask.scheduledExecutionTime), zoneId),
            namedTimerTask.isRunning
          )
        }
    }.sortBy(o => (!o.isRunning, o.executionStart.toInstant))
  }

  def cancel(uuid: UUID): Unit = {
    val it = allTasks.iterator
    breakable {
      while (it.hasNext) {
        val namedTimerTask = it.next
        if (namedTimerTask.namedTask.uuid == uuid) {
          namedTimerTask.cancel
          break
        }
      }
    }
  }

  def cancel(): Unit = {
    val it = allTasks.iterator
    while (it.hasNext) it.next.cancel
    timer.purge
  }

}

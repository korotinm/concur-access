package mk.access

import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.time.Instant
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import com.typesafe.scalalogging.LazyLogging

trait ConfigType

abstract class ConcurAccess(ttl: FiniteDuration = 5 minutes) extends LazyLogging {

  type A <: ConfigType

  private val updating = new AtomicBoolean(false)
  private val doUpdateFut = new AtomicReference[Option[Future[A]]](None)

  @volatile var data: Option[(Instant, A)] = None

  protected def access(): Future[A]

  def get()(implicit ec: ExecutionContext): Future[A] = {

    lazy val now = Instant.now
    val currVal = data

    def checkLifeTime = currVal.exists {
      case (time, _) =>
        time.plusSeconds(ttl.toSeconds).isAfter(now)
    }

    if (checkLifeTime)
      Future.successful(currVal.get._2)
    else if (updating.compareAndSet(false, true)) {

      //to prevent unnecessary updates(in different thread)
      if (checkLifeTime) {
        updating.set(false)
        Future.successful(currVal.get._2)

      } else {
        val p = Promise[A]
        val resFut = p.future
        doUpdateFut.set(Some(resFut))

        access().onComplete { tryValue =>
          tryValue match {
            case Success(value) =>
              data = Some(now, value)
              p.success(value)
            case Failure(ex) if currVal.isDefined =>
              logger.warn(s"Unsuccessful attempt to update value. Lastupdate: $currVal", ex)
              p.success(currVal.get._2)
            case Failure(ex) =>
              logger.warn(s"Unsuccessful attempt to create initial value for config", ex)
              p.failure(ex)
          }
          doUpdateFut.set(None)
          updating.set(false)
        }

        resFut
      }

    } else {
      doUpdateFut.get() match {
        case Some(fut) =>
          fut
        case None =>
          currVal
            .map(v => Future.successful(v._2))
            .getOrElse(get())
      }
    }
  }
}

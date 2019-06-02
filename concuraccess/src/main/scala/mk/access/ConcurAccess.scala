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
import scala.annotation.tailrec

trait ConfigType

abstract class ConcurAccess(ttl: FiniteDuration = 5 minutes) extends LazyLogging {

  type A <: ConfigType

  private val updating = new AtomicBoolean(false)
  private val doUpdateFut = new AtomicReference[Option[Future[A]]](None)

  @volatile var data: Option[(Instant, A)] = None

  protected def access(): Future[A]

  @tailrec
  final def get()(implicit ec: ExecutionContext): Future[A] = {
    val now = Instant.now
    val currVal = data

    def notExpired = currVal.exists {
      case (time, _) =>
        time.plusSeconds(ttl.toSeconds).isAfter(now)
    }

    notExpired match {
      case true =>
        Future.successful(currVal.get._2)

      case false if updating.compareAndSet(false, true) =>
        //to prevent unnecessary updates(in different thread)
        if (notExpired) {
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
                logger.warn(s"Unsuccessful attempt to update value. Latest data: $currVal", ex)
                p.success(currVal.get._2)
              case Failure(ex) =>
                logger.warn(s"Unsuccessful attempt to create initial value", ex)
                p.failure(ex)
            }
            doUpdateFut.set(None)
            updating.set(false)
          }

          resFut
        }

      case false =>
        doUpdateFut.get() match {
          case Some(fut) =>
            fut
          case None =>
            currVal match {
              case Some((_, value)) => Future.successful(value)
              case None             => get()
            }
        }
    }
  }
}

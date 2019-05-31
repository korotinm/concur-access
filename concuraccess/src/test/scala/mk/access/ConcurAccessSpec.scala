package mk.access

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.time.{Milliseconds, Seconds, Span}
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class ConcurAccessSpec extends WordSpec with Matchers with ScalaFutures with LazyLogging {
  implicit override val patienceConfig =
    PatienceConfig(Span(5, Seconds), Span(500, Milliseconds))

  "ConcurAccessSpec" should {
    "get BlockingStringConfig several times simultaneously" in {
      val access = new BlockingStringAccess()

      val seqFut = (1 to 100).map { _ =>
        Future(access.get())
      }

      val futSeq = Future.sequence(seqFut)

      val res100Items = futSeq.futureValue
      res100Items.foreach { v =>
        v.futureValue shouldEqual BlockingStringConfig("long time/blocking task")
      }
    }

    "get BlockingShortLifeStringConfig several times simultaneously (2 times doing update)" in {
      val access = new BlockingShortLifeStringAccess()

      val seqFut = (1 to 50).map { _ =>
        Future(access.get())
      }

      val futSeq = Future.sequence(seqFut)

      val res50Items = futSeq.futureValue

      val hCode = System.identityHashCode(res50Items.head.futureValue)

      res50Items.foreach { v =>
        v.futureValue shouldEqual BlockingShortLifeStringConfig("long lasting blocking task")
      }

      //for expirating ttl configuration on next call
      TimeUnit.MILLISECONDS.sleep(2100)

      //init step for updating
      access.get().futureValue

      val seqFut2 = (1 to 50).map { _ =>
        Future(access.get())
      }

      val futSeq2 = Future.sequence(seqFut2)

      val res50Items2 = futSeq2.futureValue

      val hCode2 = System.identityHashCode(res50Items2.head.futureValue)

      res50Items2.foreach { v =>
        v.futureValue shouldEqual BlockingShortLifeStringConfig("long lasting blocking task")
      }

      hCode should not equal hCode2
    }
  }

}

case class BlockingStringConfig(s: String) extends ConfigType
class BlockingStringAccess extends ConcurAccess {
  override type A = BlockingStringConfig
  override protected def access =
    Future {
      TimeUnit.SECONDS.sleep(2)
      BlockingStringConfig("long time/blocking task")
    }
}

case class BlockingShortLifeStringConfig(s: String) extends ConfigType
class BlockingShortLifeStringAccess extends ConcurAccess(ttl = 2 seconds) {
  override type A = BlockingShortLifeStringConfig
  override protected def access =
    Future {
      TimeUnit.SECONDS.sleep(1)
      BlockingShortLifeStringConfig("long lasting blocking task")
    }
}

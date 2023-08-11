/*
 + No License Header
 */

package org.apache.pekko.stream

import java.util.concurrent.Executors
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.language.postfixOps
import scala.util.Random
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.stream.scaladsl.{Keep, Sink, Source}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// derived from https://github.com/jaceksokol/akka-stream-map-async-partition/tree/45bdbb97cf82bf22d5decd26df18702ae81a99f9/src/test/scala/com/github/jaceksokol/akka/stream
// licensed under an MIT License
private object MapAsyncPartitionSpec {

  object TestData {

    case class BufferSize(value: Int) extends AnyVal
    case class Parallelism(value: Int) extends AnyVal

    case class TestKeyValue(key: Int, delay: FiniteDuration, value: String)

    implicit val bufferSizeArb: Arbitrary[BufferSize] = Arbitrary {
      Gen.choose(1, 100).map(BufferSize)
    }
    implicit val parallelismArb: Arbitrary[Parallelism] = Arbitrary {
      Gen.choose(2, 8).map(Parallelism)
    }
    implicit val elementsArb: Arbitrary[Seq[TestKeyValue]] = Arbitrary {
      for {
        totalElements <- Gen.choose(1, 100)
        totalPartitions <- Gen.choose(1, 8)
      } yield {
        generateElements(totalPartitions, totalElements)
      }
    }

    def generateElements(totalPartitions: Int, totalElements: Int): Seq[TestKeyValue] =
      for (i <- 1 to totalElements) yield {
        TestKeyValue(
          key = Random.nextInt(totalPartitions),
          delay = DurationInt(Random.nextInt(20) + 10).millis,
          value = i.toString)
      }

    def extractPartition(e: TestKeyValue): Int =
      e.key

    type Operation = TestKeyValue => Future[(Int, String)]

    def asyncOperation(e: TestKeyValue)(implicit ec: ExecutionContext): Future[(Int, String)] =
      Future {
        e.key -> e.value
      }

    def blockingOperation(e: TestKeyValue)(implicit ec: ExecutionContext): Future[(Int, String)] =
      Future {
        blocking {
          Thread.sleep(e.delay.toMillis)
          e.key -> e.value
        }
      }

  }
}

class MapAsyncPartitionSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with ScalaCheckDrivenPropertyChecks {

  import MapAsyncPartitionSpec.TestData._

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = 5 seconds,
    interval = 100 millis)

  private implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system")
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    super.afterAll()
  }

  it should "process elements in parallel by partition" in {
    val elements = List(
      TestKeyValue(key = 1, delay = 1000 millis, value = "1.a"),
      TestKeyValue(key = 2, delay = 700 millis, value = "2.a"),
      TestKeyValue(key = 1, delay = 500 millis, value = "1.b"),
      TestKeyValue(key = 1, delay = 500 millis, value = "1.c"),
      TestKeyValue(key = 2, delay = 900 millis, value = "2.b"))

    val result =
      Source(elements)
        .mapAsyncPartition(parallelism = 2, bufferSize = 4)(extractPartition)(blockingOperation)
        .runWith(Sink.seq)
        .futureValue
        .map(_._2)

    result shouldBe Vector("2.a", "1.a", "1.b", "2.b", "1.c")
  }

  it should "process elements in parallel preserving order in partition" in {
    forAll(minSuccessful(1000)) { (bufferSize: BufferSize, parallelism: Parallelism, elements: Seq[TestKeyValue]) =>
      val result =
        Source(elements)
          .mapAsyncPartition(parallelism.value, bufferSize.value)(extractPartition)(asyncOperation)
          .runWith(Sink.seq)
          .futureValue

      val actual = result.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
      val expected = elements.toSeq.groupBy(_.key).view.mapValues(_.map(_.value)).toMap

      actual shouldBe expected
    }
  }

  it should "process elements in sequence preserving order in partition" in {
    forAll(minSuccessful(1000)) { (bufferSize: BufferSize, elements: Seq[TestKeyValue]) =>
      val result =
        Source
          .fromIterator(() => elements.iterator)
          .mapAsyncPartition(parallelism = 1, bufferSize.value)(extractPartition)(asyncOperation)
          .runWith(Sink.seq)
          .futureValue

      val actual = result.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
      val expected = elements.toSeq.groupBy(_.key).view.mapValues(_.map(_.value)).toMap

      actual shouldBe expected
    }
  }

  it should "process elements in parallel preserving order in partition with blocking operation" in {
    forAll(minSuccessful(10)) { (bufferSize: BufferSize, parallelism: Parallelism, elements: Seq[TestKeyValue]) =>
      val result =
        Source
          .fromIterator(() => elements.iterator)
          .mapAsyncPartition(parallelism.value, bufferSize.value)(extractPartition)(blockingOperation)
          .runWith(Sink.seq)
          .futureValue

      val actual = result.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
      val expected = elements.toSeq.groupBy(_.key).view.mapValues(_.map(_.value)).toMap

      actual shouldBe expected
    }
  }

  it should "stop the stream via a KillSwitch" in {
    val (killSwitch, future) =
      Source(LazyList.from(1))
        .mapAsyncPartition(parallelism = 6)(i => i % 6) { i =>
          Future {
            blocking {
              Thread.sleep(40)
              (i % 6).toString -> i.toString
            }
          }
        }
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.seq)(Keep.both)
        .run()

    Thread.sleep(500)

    killSwitch.shutdown()

    val result = future.futureValue.groupBy(_._1)
    result should have size 6
    result.values.foreach {
      _.size should be >= 10
    }
  }

  it should "stop the stream if any operation fail" in {
    val future =
      Source(LazyList.from(1))
        .mapAsyncPartition(parallelism = 4)(i => i % 8) { i =>
          Future {
            if (i == 23) throw new RuntimeException("Ignore it")
            else i.toString
          }
        }
        .toMat(Sink.ignore)(Keep.right)
        .run()

    future.failed.futureValue shouldBe a[RuntimeException]
  }

}

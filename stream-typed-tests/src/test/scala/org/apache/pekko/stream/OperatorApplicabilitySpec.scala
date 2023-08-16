/*
 * No License Header (original 3rd party file has no license header, see below)
 */

package org.apache.pekko.stream

import scala.annotation.nowarn
import scala.concurrent.{ ExecutionContext, Future }
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.stream.scaladsl.{ Flow, FlowWithContext, Sink, Source, SourceWithContext }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// derived from https://github.com/jaceksokol/akka-stream-map-async-partition/tree/45bdbb97cf82bf22d5decd26df18702ae81a99f9/src/test/scala/com/github/jaceksokol/akka/stream/OperatorApplicabilitySpec.scala
// licensed under an MIT License
class OperatorApplicabilitySpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  private implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system")
  private implicit val ec: ExecutionContext = system.executionContext

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    super.afterAll()
  }

  @nowarn("msg=never used")
  private def f(i: Int, p: Int): Future[Int] =
    Future(i % 2)

  it should "be applicable to a source" in {
    Source
      .single(3)
      .mapAsyncPartitioned(parallelism = 1)(identity)(f)
      .runWith(Sink.seq)
      .futureValue shouldBe Seq(1)
  }

  it should "be applicable to a source with context" in {
    SourceWithContext
      .fromTuples(Source.single(3 -> "A"))
      .mapAsyncPartitioned(parallelism = 1)(identity)(f)
      .runWith(Sink.seq)
      .futureValue shouldBe Seq(1 -> "A")
  }

  it should "be applicable to a flow" in {
    Flow[Int]
      .mapAsyncPartitioned(parallelism = 1)(identity)(f)
      .runWith(Source.single(3), Sink.seq)
      ._2
      .futureValue shouldBe Seq(1)
  }

  it should "be applicable to a flow with context" in {
    val flow =
      FlowWithContext[Int, String]
        .mapAsyncPartitioned(parallelism = 1)(identity)(f)

    SourceWithContext
      .fromTuples(Source.single(3 -> "A"))
      .via(flow)
      .runWith(Sink.seq)
      .futureValue shouldBe Seq(1 -> "A")
  }

}

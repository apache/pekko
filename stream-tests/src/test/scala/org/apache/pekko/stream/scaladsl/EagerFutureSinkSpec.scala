/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.Done
import pekko.stream.AbruptStageTerminationException
import pekko.stream.Attributes
import pekko.stream.Inlet
import pekko.stream.Materializer
import pekko.stream.Outlet
import pekko.stream.SinkShape
import pekko.stream.SourceShape
import pekko.stream.stage.AsyncCallback
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageWithMaterializedValue
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils._
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped

class EagerFutureSinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 1
    pekko.stream.materializer.max-input-buffer-size = 1
  """) {

  val ex = TE("")

  private final class SourceControl(
      val firstPull: Future[Done],
      signal: AsyncCallback[(Option[Int], Boolean)]) {
    def send(value: Int, last: Boolean = false): Future[Done] =
      signal.invokeWithFeedback((Some(value), last))

    def finish(): Future[Done] = signal.invokeWithFeedback((None, true))
  }

  private final class ControlledSource extends GraphStageWithMaterializedValue[SourceShape[Int], SourceControl] {
    private val out = Outlet[Int]("eagerSinkTestSource.out")
    override val shape: SourceShape[Int] = SourceShape(out)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, SourceControl) = {
      val firstPull = Promise[Done]()
      class Logic extends GraphStageLogic(shape) with OutHandler {
        val signal: AsyncCallback[(Option[Int], Boolean)] = getAsyncCallback {
          case (element, last) =>
            element.foreach(push(out, _))
            if (last) completeStage()
        }
        override def onPull(): Unit = {
          firstPull.trySuccess(Done)
          ()
        }
        setHandler(out, this)
      }
      val logic = new Logic
      (logic, new SourceControl(firstPull.future, logic.signal))
    }
  }

  private final class SinkControl(val demand: AsyncCallback[Unit], val received: Future[Int], val completed: Future[Done])

  private final class ControlledSink extends GraphStageWithMaterializedValue[SinkShape[Int], SinkControl] {
    private val in = Inlet[Int]("eagerSinkTestSink.in")
    override val shape: SinkShape[Int] = SinkShape(in)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, SinkControl) = {
      val received = Promise[Int]()
      val completed = Promise[Done]()
      class Logic extends GraphStageLogic(shape) with InHandler {
        val demand: AsyncCallback[Unit] = getAsyncCallback(_ => pull(in))
        override def onPush(): Unit = {
          received.success(grab(in))
          ()
        }
        override def onUpstreamFinish(): Unit = {
          completed.success(Done)
          completeStage()
        }
        override def onUpstreamFailure(ex: Throwable): Unit = {
          received.tryFailure(ex)
          completed.tryFailure(ex)
          failStage(ex)
        }
        setHandler(in, this)
      }
      val logic = new Logic
      (logic, new SinkControl(logic.demand, received.future, completed.future))
    }
  }

  private def startHandoff(): (SourceControl, SinkControl) = {
    val sinkPromise = Promise[Sink[Int, SinkControl]]()
    // No async boundary: both controlled stages and the eager sink's substream use the same
    // interpreter actor. It processes the current graph events before the next test callback.
    val (upstream, inner) = Source.fromGraph(new ControlledSource)
      .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.both)
      .run()
    upstream.firstPull.futureValue shouldBe Done
    sinkPromise.success(Sink.fromGraph(new ControlledSink))
    (upstream, inner.futureValue)
  }

  "Sink.eagerFutureSink" must {

    "work with an already-completed future" in {
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe Seq(1, 2, 3)
    }

    "work when the future completes after elements arrive" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.futureValue shouldBe Seq(1, 2, 3)
    }

    "reuse the outstanding prefetch when the inner sink requests first" in assertAllStagesStopped {
      val (upstream, inner) = startHandoff()

      inner.demand.invokeWithFeedback(()).futureValue shouldBe Done
      upstream.send(1).futureValue shouldBe Done
      inner.received.futureValue shouldBe 1
      upstream.finish().futureValue shouldBe Done
      inner.completed.futureValue shouldBe Done
    }

    "buffer the prefetched element when it arrives before the inner sink requests" in assertAllStagesStopped {
      val (upstream, inner) = startHandoff()

      upstream.send(2).futureValue shouldBe Done
      inner.demand.invokeWithFeedback(()).futureValue shouldBe Done
      inner.received.futureValue shouldBe 2
      upstream.finish().futureValue shouldBe Done
      inner.completed.futureValue shouldBe Done
    }

    "deliver a buffered last element before completing the inner sink" in assertAllStagesStopped {
      val (upstream, inner) = startHandoff()

      upstream.send(3, last = true).futureValue shouldBe Done
      inner.demand.invokeWithFeedback(()).futureValue shouldBe Done
      inner.received.futureValue shouldBe 3
      inner.completed.futureValue shouldBe Done
    }

    "handle an empty stream with an already-completed future" in {
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe Seq.empty
    }

    "handle an empty stream with a pending future" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.futureValue shouldBe Seq.empty
    }

    "propagate failure when the future fails" in {
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(Future.failed[Sink[Int, Future[Seq[Int]]]](ex)))(Keep.right)
        .run()
        .flatten

      result.failed.futureValue shouldBe ex
    }

    "propagate upstream failure" in {
      val result = Source
        .failed[Int](ex)
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.failed.futureValue shouldBe ex
    }

    "propagate upstream failure when the future is still pending" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source
        .failed[Int](ex)
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.failed.futureValue shouldBe ex
    }

    "propagate upstream failure when element was buffered and future resolves later" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source(List(1))
        .concat(Source.failed[Int](ex))
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()
        .flatten

      sinkPromise.success(Sink.seq[Int])
      result.failed.futureValue shouldBe ex
    }

    "work with Sink.fold on a non-empty stream" in {
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.fold[Int, Int](0)(_ + _))))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe 6
    }

    "work with Sink.fold on an empty stream" in {
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.fold[Int, Int](0)(_ + _))))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe 0
    }

    "not throw NeverMaterializedException on empty stream (unlike futureSink)" in {
      val result = Source
        .empty[Int]
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.seq[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe Seq.empty
    }

    "materialize inner sink immediately when the future is already completed (even with no elements yet)" in {
      val innerMatPromise = Promise[Unit]()
      val sink = Sink.foreach[Int](_ => ()).mapMaterializedValue(_ => innerMatPromise.success(()))
      val sinkFuture = Future.successful(sink)

      Source.maybe[Int]
        .toMat(Sink.eagerFutureSink(sinkFuture))(Keep.right)
        .run()

      innerMatPromise.future.futureValue shouldBe (())
    }

    "cancel upstream when inner sink cancels" in {
      val result = Source(List(1, 2, 3, 4, 5))
        .toMat(Sink.eagerFutureSink(Future.successful(Sink.head[Int])))(Keep.right)
        .run()
        .flatten

      result.futureValue shouldBe 1
    }

    "propagate failure when the future fails late" in {
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source(List(1, 2, 3))
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()

      sinkPromise.failure(ex)
      result.failed.futureValue shouldBe ex
    }

    "fail the materialized value on abrupt termination before future completion" in {
      val mat = Materializer(system)
      val sinkPromise = Promise[Sink[Int, Future[Seq[Int]]]]()
      val result = Source.maybe[Int]
        .toMat(Sink.eagerFutureSink(sinkPromise.future))(Keep.right)
        .run()(mat)

      mat.shutdown()

      result.failed.futureValue shouldBe an[AbruptStageTerminationException]
    }
  }
}

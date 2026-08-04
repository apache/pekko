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

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.{ ExecutionContext, Promise }
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler }
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSource

class SinkWatchTerminationSpec extends StreamSpec {

  "A Sink.watchTermination" must {

    "complete future with success when stream is completed" in {
      val done = Source(1 to 4).runWith(Sink.ignore.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future with success when the stream is empty" in {
      val done = Source.empty[Int].runWith(Sink.ignore.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future with success when the sink cancels itself" in {
      val done = Source(1 to 4).runWith(Sink.head[Int].watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "keep the original materialized value" in {
      val (head, done) = Source(1 to 4).runWith(Sink.head[Int].watchTermination(Keep.both))
      head.futureValue should ===(1)
      done.futureValue should ===(Done)
    }

    "keep materialized value transformations of the wrapped sink" in {
      val transformed: Sink[Int, scala.concurrent.Future[Int]] =
        Sink.headOption[Int].mapMaterializedValue(_.map(_.getOrElse(0))(ExecutionContext.parasitic))
      val (head, done) = Source(1 to 4).runWith(transformed.watchTermination(Keep.both))
      head.futureValue should ===(1)
      done.futureValue should ===(Done)
    }

    "fail future when stream is failed" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, done) = TestSource[Int]().toMat(Sink.ignore.watchTermination(Keep.right))(Keep.both).run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(done.failed) { _ shouldBe ex }
    }

    "complete future only after the postStop of the wrapped sink has run" in {
      val events = new ConcurrentLinkedQueue[String]()

      class PostStopSignalingSink extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("PostStopSignalingSink.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def postStop(): Unit = events.add("postStop")
            setHandler(in, this)
          }
      }

      val done = Source(1 to 4).runWith(Sink.fromGraph(new PostStopSignalingSink).watchTermination(Keep.right))
      done.onComplete(_ => events.add("futureCompleted"))(ExecutionContext.parasitic)
      done.futureValue should ===(Done)
      events.asScala.toList should ===(List("postStop", "futureCompleted"))
    }

    "fail future when stream abruptly terminated" in {
      val mat = Materializer(system)
      val done = TestSource[Int]().toMat(Sink.ignore.watchTermination(Keep.right))(Keep.both).run()(mat)._2
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptTerminationException]
    }

    "reject composite sinks consisting of multiple stages" in {
      val ex = intercept[IllegalArgumentException] {
        Sink.foreach[Int](println).watchTermination(Keep.right)
      }
      ex.getMessage should include("single stage")
    }

    "reject sinks created with Sink.combine" in {
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      intercept[IllegalArgumentException] {
        combined.watchTermination(Keep.right)
      }
    }

    "work with Sink.queue" in {
      val (queue, done) = Source(1 to 4).runWith(Sink.queue[Int]().watchTermination(Keep.both))
      queue.pull().futureValue should ===(Some(1))
      queue.pull().futureValue should ===(Some(2))
      queue.cancel()
      done.futureValue should ===(Done)
    }

    "signal termination once after single materialization value promise completed" in {
      val terminationSignal = Promise[Done]()

      class CompletingSink extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("CompletingSink.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def onUpstreamFinish(): Unit = {
              terminationSignal.trySuccess(Done)
              completeStage()
            }
            setHandler(in, this)
          }
      }

      val done = Source(1 to 4).runWith(Sink.fromGraph(new CompletingSink).watchTermination(Keep.right))
      terminationSignal.future.futureValue should ===(Done)
      done.futureValue should ===(Done)
    }

    "fail future when stream is failed after the wrapped sink swapped its inlet handler" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, done) = TestSource[Int]()
        .toMat(Sink.lazySink(() => Sink.ignore).watchTermination(Keep.right))(Keep.both)
        .run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(done.failed) { _ shouldBe ex }
    }

    "fail future when a handler of the wrapped sink throws" in {
      class FailingSink extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("FailingSink.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = throw new RuntimeException("boom") with NoStackTrace
            setHandler(in, this)
          }
      }

      val done = Source.single(1).runWith(Sink.fromGraph(new FailingSink).watchTermination(Keep.right))
      done.failed.futureValue shouldBe a[RuntimeException]
    }

    "fail future when a fully fused stream abruptly terminated" in {
      val mat = Materializer(system)
      val done = Source.maybe[Int].toMat(Sink.ignore.watchTermination(Keep.right))(Keep.right).run()(mat)
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptStageTerminationException]
    }

    "fail future when upstream of Sink.queue fails" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, (queue, done)) =
        TestSource[Int]()
          .toMat(Sink.queue[Int]().watchTermination(Keep.both))(Keep.both)
          .run()
      p.sendNext(1)
      queue.pull().futureValue should ===(Some(1))
      p.sendError(ex)
      queue.pull().failed.futureValue shouldBe ex
      whenReady(done.failed) { _ shouldBe ex }
    }

    "work with a sink behind an async island" in {
      val done = Source(1 to 4).runWith(Sink.ignore.async.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }
  }
}

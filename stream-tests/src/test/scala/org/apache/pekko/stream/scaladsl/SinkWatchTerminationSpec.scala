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

import java.util.concurrent.{ ConcurrentLinkedQueue, CyclicBarrier }
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import pekko.stream.testkit.scaladsl.TestSource

class SinkWatchTerminationSpec extends StreamSpec {

  "A Sink.watchTermination" must {

    for (failurePhase <- List("preStart", "onPush", "upstream"); watchDepth <- 0 to 2) {
      s"preserve substream failure from $failurePhase with $watchDepth watches" in {
        val ex = new RuntimeException(s"$failurePhase failed") with NoStackTrace
        val substream = Promise[Seq[Int]]()
        val stage = new GraphStage[SinkShape[Int]] {
          val in = Inlet[Int]("substreamFailure.in")
          override val shape = SinkShape(in)
          override def createLogic(attributes: Attributes): GraphStageLogic =
            new GraphStageLogic(shape) with InHandler {
              override def preStart(): Unit = {
                val out = new SubSourceOutlet[Int]("substreamFailure.out")
                out.setHandler(new OutHandler {
                  override def onPull(): Unit = ()
                })
                substream.completeWith(Source.fromGraph(out.source).runWith(Sink.seq[Int])(subFusingMaterializer))
                if (failurePhase == "preStart") throw ex
                pull(in)
              }
              override def onPush(): Unit = throw ex
              setHandler(in, this)
            }
        }
        val sink = (0 until watchDepth).foldLeft(
          Sink.fromGraph(stage).mapMaterializedValue(_ => Future.successful[Done](Done))) {
          case (inner, _) => inner.watchTermination(Keep.right)
        }
        val source = if (failurePhase == "upstream") Source.failed[Int](ex) else Source.single(1)
        val done = source.runWith(sink)
        substream.future.failed.futureValue shouldBe ex
        if (watchDepth > 0) done.failed.futureValue shouldBe ex
      }
    }

    for (asyncStop <- List(false, true); watched <- List(false, true)) {
      s"run cleanup when postStop throws (async self-stop: $asyncStop, watched: $watched)" in {
        val ex = new RuntimeException("postStop failed") with NoStackTrace
        val cleanedUp = Promise[Done]()
        val stage = new GraphStage[SinkShape[Int]] {
          val in = Inlet[Int]("postStopFailure.in")
          override val shape = SinkShape(in)
          override def createLogic(attributes: Attributes): GraphStageLogic =
            new GraphStageLogic(shape) with InHandler {
              override def preStart(): Unit =
                if (asyncStop) getAsyncCallback[Unit](_ => completeStage()).invoke(())
                else pull(in)
              override def onPush(): Unit = pull(in)
              override def postStop(): Unit = throw ex
              override protected[stream] def afterPostStop(): Unit = {
                super.afterPostStop()
                cleanedUp.success(Done)
              }
              setHandler(in, this)
            }
        }
        val source = if (asyncStop) Source.maybe[Int] else Source.empty[Int]
        val sink = Sink.fromGraph(stage)
        if (watched) source.runWith(sink.watchTermination(Keep.right)).failed.futureValue shouldBe ex
        else source.runWith(sink)
        cleanedUp.future.futureValue shouldBe Done
      }
    }

    "record a failure from a handler installed in preStart" in {
      val ex = new RuntimeException("upstream finish failed") with NoStackTrace
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("handlerSwap.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) {
            setHandler(in, eagerTerminateInput)
            override def preStart(): Unit = {
              setHandler(in,
                new InHandler {
                  override def onPush(): Unit = pull(in)
                  override def onUpstreamFinish(): Unit = throw ex
                })
              pull(in)
            }
          }
      }
      val done = Source.empty[Int].runWith(Sink.fromGraph(stage).watchTermination(Keep.right))
      done.failed.futureValue shouldBe ex
    }

    "complete only after subclass cleanup following afterPostStop" in {
      val termination = new AtomicReference[Future[Done]]()
      val completedBeforeCleanup = Promise[Boolean]()
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("cleanup.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override protected[stream] def afterPostStop(): Unit = {
              super.afterPostStop()
              completedBeforeCleanup.success(termination.get().isCompleted)
            }
            setHandler(in, this)
          }
      }
      val sink = Sink.fromGraph(stage).watchTermination { (_, done) =>
        termination.set(done)
        done
      }
      Source.empty[Int].runWith(sink).futureValue shouldBe Done
      completedBeforeCleanup.future.futureValue shouldBe false
    }

    "report an observed failure even if the wrapped graph recovers" in {
      val ex = new RuntimeException("recovered failure") with NoStackTrace
      val sink = Flow[Int].recover { case _ => 42 }.toMat(Sink.seq[Int])(Keep.right)
      val (elements, done) = Source.failed[Int](ex).runWith(sink.watchTermination(Keep.both))
      elements.futureValue shouldBe Seq(42)
      done.failed.futureValue shouldBe ex
    }

    "use a fresh tracker after a materialized-value combiner throws" in {
      val ex = new RuntimeException("materialization failed") with NoStackTrace
      var first: Future[Done] = null
      val watched = Sink.ignore.watchTermination { (_, done) =>
        if (first eq null) {
          first = done
          throw ex
        }
        done
      }
      intercept[RuntimeException](Source.empty[Int].runWith(watched)) shouldBe ex
      val second = Source.empty[Int].runWith(watched)
      (second eq first) shouldBe false
      second.futureValue shouldBe Done
    }

    "detect abrupt termination while waiting after upstream completion" in {
      val upstreamFinished = Promise[Done]()
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("keepGoing.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def onUpstreamFinish(): Unit = {
              setKeepGoing(true)
              upstreamFinished.success(Done)
            }
            setHandler(in, this)
          }
      }
      val mat = Materializer(system)
      val done = Source.empty[Int].runWith(Sink.fromGraph(stage).watchTermination(Keep.right))(mat)
      try {
        upstreamFinished.future.futureValue shouldBe Done
        mat.shutdown()
        done.failed.futureValue shouldBe an[AbruptStageTerminationException]
      } finally mat.shutdown()
    }

    "detect abrupt termination after one composite branch cancels" in {
      val mat = Materializer(system)
      val combined = Sink.fromGraph(GraphDSL.createGraph(Sink.head[Int]) { implicit b => head =>
        import GraphDSL.Implicits._
        val broadcast = b.add(Broadcast[Int](2))
        broadcast ~> head
        broadcast ~> Sink.ignore
        SinkShape(broadcast.in)
      })
      val (source, (head, done)) = TestSource[Int]().toMat(combined.watchTermination(Keep.both))(Keep.both).run()(mat)
      try {
        source.sendNext(1)
        head.futureValue shouldBe 1
        mat.shutdown()
        done.failed.futureValue shouldBe an[AbruptTerminationException]
      } finally mat.shutdown()
    }

    "keep nested termination observers independent" in {
      val (inner, outer) =
        Source.empty[Int].runWith(Sink.ignore.watchTermination(Keep.right).watchTermination(Keep.both))
      (inner eq outer) shouldBe false
      inner.futureValue shouldBe Done
      outer.futureValue shouldBe Done
    }

    "isolate reentrant materializations of the same blueprint" in {
      var reenter = true
      var nested: Future[Done] = null
      lazy val watched: Sink[Any, Future[Done]] = Sink.ignore.watchTermination { (_, done) =>
        if (reenter) {
          reenter = false
          nested = Source.empty[Int].runWith(watched)
        }
        done
      }
      val outer = Source.empty[Int].runWith(watched)
      (outer eq nested) shouldBe false
      outer.futureValue shouldBe Done
      nested.futureValue shouldBe Done
    }

    "fail after a stage actor stops the sink and postStop throws" in {
      val ex = new RuntimeException("stage actor postStop failed") with NoStackTrace
      val actor = Promise[pekko.actor.ActorRef]()
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("stageActor.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = actor.success(getStageActor { case (_, _) => completeStage() }.ref)
            override def onPush(): Unit = ()
            override def postStop(): Unit = throw ex
            setHandler(in, this)
          }
      }
      val done = Source.maybe[Int].runWith(Sink.fromGraph(stage).watchTermination(Keep.right))
      actor.future.futureValue ! Done
      done.failed.futureValue shouldBe ex
    }

    "record an async failure after all ports have closed" in {
      val ex = new RuntimeException("async commit failed") with NoStackTrace
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("asyncFailure.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def onUpstreamFinish(): Unit = {
              setKeepGoing(true)
              getAsyncCallback[Unit](_ => failStage(ex)).invoke(())
            }
            setHandler(in, this)
          }
      }
      val done = Source.empty[Int].runWith(Sink.fromGraph(stage).watchTermination(Keep.right))
      done.failed.futureValue shouldBe ex
    }

    "record a failure cancellation initiated by the sink" in {
      val ex = new RuntimeException("cancelled with failure") with NoStackTrace
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("failureCancellation.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = cancel(in, ex)
            override def onPush(): Unit = ()
            setHandler(in, this)
          }
      }
      val done = Source.maybe[Int].runWith(Sink.fromGraph(stage).watchTermination(Keep.right))
      done.failed.futureValue shouldBe ex
    }

    "materialize independently after createLogic throws" in {
      val ex = new RuntimeException("createLogic failed") with NoStackTrace
      var fail = true
      val stage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("materializationFailure.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic = {
          if (fail) {
            fail = false
            throw ex
          }
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            setHandler(in, this)
          }
        }
      }
      val watched = Sink.combine(Sink.ignore.async, Sink.fromGraph(stage).async)(Broadcast[Int](_))
        .watchTermination(Keep.right)
      intercept[RuntimeException](Source.empty[Int].runWith(watched)) shouldBe ex
      Source.empty[Int].runWith(watched).futureValue shouldBe Done
      Source.empty[Int].runWith(watched).futureValue shouldBe Done
    }

    "wait for all async islands to stop even when one postStop fails" in {
      val ex = new RuntimeException("composite postStop failed") with NoStackTrace
      val stopped = Promise[Done]()
      val finish = Promise[pekko.stream.stage.AsyncCallback[Unit]]()
      val failedStage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("failedIsland.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def postStop(): Unit = {
              stopped.success(Done)
              throw ex
            }
            setHandler(in, this)
          }
      }
      val waitingStage = new GraphStage[SinkShape[Int]] {
        val in = Inlet[Int]("waitingIsland.in")
        override val shape = SinkShape(in)
        override def createLogic(attributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def onUpstreamFinish(): Unit = {
              setKeepGoing(true)
              finish.success(getAsyncCallback[Unit](_ => completeStage()))
            }
            setHandler(in, this)
          }
      }
      val combined =
        Sink.combine(Sink.fromGraph(failedStage).async, Sink.fromGraph(waitingStage).async)(Broadcast[Int](_))
      val done = Source.empty[Int].runWith(combined.watchTermination(Keep.right))
      stopped.future.futureValue shouldBe Done
      val callback = finish.future.futureValue
      done.isCompleted shouldBe false
      callback.invoke(())
      done.failed.futureValue shouldBe ex
    }

    "reject sinks containing non-GraphStage modules" in {
      val subscriber = TestSubscriber.manualProbe[Int]()
      intercept[IllegalArgumentException](Sink.fromSubscriber(subscriber).watchTermination(Keep.right))
    }

    "preserve the result of a composite fold sink" in {
      val (sum, done) = Source(1 to 4).runWith(Sink.fold[Int, Int](0)(_ + _).watchTermination(Keep.both))
      sum.futureValue shouldBe 10
      done.futureValue shouldBe Done
    }

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
      done.map { value =>
        events.add("futureCompleted")
        value
      }(ExecutionContext.parasitic).futureValue should ===(Done)
      events.asScala.toList should ===(List("postStop", "futureCompleted"))
    }

    "fail future when stream abruptly terminated" in {
      val mat = Materializer(system)
      val done = TestSource[Int]().toMat(Sink.ignore.watchTermination(Keep.right))(Keep.both).run()(mat)._2
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptTerminationException]
    }

    "complete future for a composite sink built with Sink.foreach" in {
      val done = Source(1 to 4).runWith(Sink.foreach[Int](_ => ()).watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future for a composite sink built with Sink.combine" in {
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      val done = Source(1 to 4).runWith(combined.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "complete future for a composite sink built with GraphDSL" in {
      val composite = Sink.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        val s1 = b.add(Sink.ignore)
        val s2 = b.add(Sink.ignore)
        bcast.out(0) ~> s1
        bcast.out(1) ~> s2
        SinkShape(bcast.in)
      })
      val done = Source(1 to 4).runWith(composite.watchTermination(Keep.right))
      done.futureValue should ===(Done)
    }

    "fail future when upstream fails on a composite sink" in {
      val ex = new RuntimeException("composite fail") with NoStackTrace
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      val (p, done) = TestSource[Int]().toMat(combined.watchTermination(Keep.right))(Keep.both).run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(done.failed) { _ shouldBe ex }
    }

    "complete future only after all stages' postStop have run in a composite sink" in {
      val events = new ConcurrentLinkedQueue[String]()

      class SignalingSink(name: String) extends GraphStage[SinkShape[Int]] {
        val in = Inlet[Int](s"$name.in")
        override val shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler {
            override def preStart(): Unit = pull(in)
            override def onPush(): Unit = pull(in)
            override def postStop(): Unit = events.add(name)
            setHandler(in, this)
          }
      }

      val composite = Sink.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        val s1 = b.add(new SignalingSink("s1"))
        val s2 = b.add(new SignalingSink("s2"))
        bcast.out(0) ~> s1
        bcast.out(1) ~> s2
        SinkShape(bcast.in)
      })

      val done = Source(1 to 4).runWith(composite.watchTermination(Keep.right))
      done.map { value =>
        events.add("futureCompleted")
        value
      }(ExecutionContext.parasitic).futureValue should ===(Done)
      val list = events.asScala.toList
      list.last should ===("futureCompleted")
      list.filterNot(_ == "futureCompleted").toSet should ===(Set("s1", "s2"))
    }

    "keep the original materialized value of a composite sink" in {
      val composite = Sink.fromGraph(GraphDSL.createGraph(Sink.queue[Int]()) { implicit b => queue =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        bcast.out(0) ~> queue
        bcast.out(1) ~> Sink.ignore
        SinkShape(bcast.in)
      })
      val (queue, done) = Source(1 to 4).runWith(composite.watchTermination(Keep.both))
      queue.pull().futureValue should ===(Some(1))
      queue.cancel()
      done.futureValue should ===(Done)
    }

    "fail future when a fully fused composite stream abruptly terminated" in {
      val mat = Materializer(system)
      val combined = Sink.combine(Sink.ignore, Sink.ignore)(Broadcast[Int](_))
      val done = Source.maybe[Int].toMat(combined.watchTermination(Keep.right))(Keep.right).run()(mat)
      mat.shutdown()
      done.failed.futureValue shouldBe an[AbruptStageTerminationException]
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

    "complete future when stream completes after the wrapped sink swapped its inlet handler" in {
      val (p, done) = TestSource[Int]()
        .toMat(Sink.lazySink(() => Sink.ignore).watchTermination(Keep.right))(Keep.both)
        .run()
      p.sendNext(1)
      p.sendNext(2)
      p.sendNext(3)
      p.sendComplete()
      done.futureValue should ===(Done)
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

    "produce independent futures when the same blueprint is materialized multiple times" in {
      val watched = Sink.ignore.watchTermination(Keep.right)
      val done1 = Source(1 to 4).runWith(watched)
      val done2 = Source(5 to 8).runWith(watched)
      done1.futureValue should ===(Done)
      done2.futureValue should ===(Done)
      (done1 should not).be(done2)
    }

    "produce independent futures when the same blueprint is materialized concurrently" in {
      implicit val ec: ExecutionContext = system.dispatcher
      val watched = Sink.ignore.watchTermination(Keep.right)
      // Regression test: Sink blueprints must be safely re-materializable from concurrent threads.
      // A prior implementation shared a single mutable tracker field across all materializations of
      // the same blueprint, which raced when two threads materialized it at the same time and could
      // hang one of the resulting futures forever.
      for (_ <- 1 to 50) {
        val barrier = new CyclicBarrier(2)
        val f1 = Future {
          barrier.await()
          Source(1 to 3).runWith(watched)
        }.flatMap(identity)
        val f2 = Future {
          barrier.await()
          Source(4 to 6).runWith(watched)
        }.flatMap(identity)
        Await.result(f1, 5.seconds) should ===(Done)
        Await.result(f2, 5.seconds) should ===(Done)
      }
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.concurrent.{ duration, Await, Promise }

import duration._

import org.apache.pekko
import pekko.Done
import pekko.stream.QueueOfferResult
import pekko.stream.impl.UnfoldResourceSource
import pekko.stream.impl.fusing.GraphInterpreter
import pekko.stream.scaladsl._
import pekko.stream.stage.GraphStage
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils.TE
import pekko.stream.testkit.scaladsl.TestSink

class FusingSpec extends StreamSpec {

  val asyncBoundaryInputBuffer = Attributes.inputBuffer(16, 16)

  def actorRunningStage = {
    GraphInterpreter.currentInterpreter.context
  }

  val snitchFlow = Flow[Int].map(x => { testActor ! actorRunningStage; x }).async

  "SubFusingActorMaterializer" must {

    "work with asynchronous boundaries in the subflows" in {
      val async = Flow[Int].map(_ * 2).async
      Source(0 to 9)
        .map(_ * 10)
        .flatMapMerge(5, i => Source(i to (i + 9)).via(async))
        .grouped(1000)
        .runWith(Sink.head)
        .futureValue
        .sorted should ===(0 to 198 by 2)
    }

    "preserve elements across repeated asynchronous boundary batches" in {
      val elements = 1 to 5000

      Source(elements)
        .map(identity)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(Sink.seq)
        .futureValue should ===(elements)
    }

    "preserve elements across chained asynchronous boundary batches" in {
      val elements = 1 to 5000

      Source(elements)
        .map(identity)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .map(identity)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(Sink.seq)
        .futureValue should ===(elements)
    }

    "flush asynchronous boundary batches when async input suspends the interpreter" in {
      val elements = 1 to 64
      val (queue, downstream) = Source
        .fromGraph(Source.queue[Int](elements.size))
        .async
        .addAttributes(ActorAttributes.syncProcessingLimit(1) and asyncBoundaryInputBuffer)
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      downstream.request(elements.size)
      elements.foreach { elem =>
        queue.offer(elem) should ===(QueueOfferResult.Enqueued)
      }
      downstream.expectNextN(elements)

      queue.complete()
      downstream.expectComplete()
    }

    "drain asynchronous boundary batches before completing" in {
      val elements = 1 to 64

      Source(elements)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(TestSink[Int]())
        .request(elements.size)
        .expectNextN(elements)
        .expectComplete()
    }

    "drain asynchronous boundary batches before failing" in {
      val elements = 1 to 64
      val ex = TE("boom")
      val upstream = TestPublisher.probe[Int]()
      val downstream = Source
        .fromPublisher(upstream)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(TestSink[Int]())

      downstream.request(elements.size + 1)

      var sent = 0
      while (sent < elements.size) {
        val request = upstream.expectRequest()
        val send = math.min(request, elements.size - sent).toInt
        elements.slice(sent, sent + send).foreach(upstream.sendNext)
        sent += send
      }

      upstream.sendError(ex)
      downstream.expectNextN(elements)
      downstream.expectError(ex)
    }

    "not emit elements after an asynchronous boundary failure" in {
      val ex = TE("boom")
      val probe = Source(1 to 64)
        .concat(Source.failed(ex))
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(TestSink[Int]())

      probe.request(65)

      var expected = 1
      var errorSignalled = false
      while (!errorSignalled && expected <= 64) {
        probe.expectNextOrError(expected, ex) match {
          case Right(_) =>
            expected += 1
          case Left(_) =>
            errorSignalled = true
        }
      }
      if (!errorSignalled) probe.expectError(ex)
    }

    "propagate cancellation with asynchronous boundary elements in flight" in {
      val upstream = TestPublisher.probe[Int]()
      val downstream = Source
        .fromPublisher(upstream)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(TestSink[Int]())

      downstream.request(16)
      upstream.expectRequest() should be >= 16L
      (1 to 16).foreach(upstream.sendNext)

      downstream.expectNext(1)
      downstream.cancel()

      upstream.expectCancellation()
    }

    "not exceed downstream demand across asynchronous boundary batches" in {
      val downstream = Source(1 to 1000)
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .runWith(TestSink[Int]())

      downstream.request(1)
      downstream.expectNext(1)
      downstream.expectNoMessage(100.millis)

      downstream.request(2)
      downstream.expectNext(2, 3)
      downstream.expectNoMessage(100.millis)

      downstream.cancel()
    }

    "preserve resuming supervision across asynchronous boundary batches" in {
      Source(List(1, 2, -1, 3, 4))
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .map { elem =>
          require(elem > 0)
          elem
        }
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(Sink.seq)
        .futureValue should ===(Seq(1, 2, 3, 4))
    }

    "preserve restarting supervision across asynchronous boundary batches" in {
      Source(List(1, 3, -1, 5, 7))
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .scan(0) { (old, current) =>
          require(current > 0)
          old + current
        }
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(Sink.seq)
        .futureValue should ===(Seq(0, 1, 4, 0, 5, 12))
    }

    "preserve stopping supervision across asynchronous boundary batches" in {
      val ex = TE("boom")
      Source(List(1, 2, -1, 3, 4))
        .async
        .addAttributes(asyncBoundaryInputBuffer)
        .map {
          case -1   => throw ex
          case elem => elem
        }
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(TestSink[Int]())
        .request(5)
        .expectNext(1, 2)
        .expectError(ex)
    }

    "use multiple actors when there are asynchronous boundaries in the subflows (manual)" in {
      val async = Flow[Int].map(x => { testActor ! actorRunningStage; x }).async
      Source(0 to 9)
        .via(snitchFlow.async)
        .flatMapMerge(5, i => Source.single(i).via(async))
        .grouped(1000)
        .runWith(Sink.head)
        .futureValue
        .sorted should ===(0 to 9)
      val refs = receiveN(20)
      refs.toSet should have size 11 // main flow + 10 subflows
    }

    "use multiple actors when there are asynchronous boundaries in the subflows (operator)" in {
      Source(0 to 9)
        .via(snitchFlow)
        .flatMapMerge(5, i => Source.single(i).via(snitchFlow.async))
        .grouped(1000)
        .runWith(Sink.head)
        .futureValue
        .sorted should ===(0 to 9)
      val refs = receiveN(20)
      refs.toSet should have size 11 // main flow + 10 subflows
    }

    "use one actor per grouped substream when there is an async boundary around the flow (manual)" in {
      val in = 0 to 9
      Source(in)
        .via(snitchFlow)
        .groupBy(in.size, identity)
        .via(snitchFlow.async)
        .mergeSubstreams
        .runWith(Sink.seq)
        .futureValue
        .sorted should ===(in)
      val refs = receiveN(in.size + in.size) // each element through the first map, then the second map

      refs.toSet should have size (in.size + 1) // outer/main actor + 1 actor per subflow
    }

    "use one actor per grouped substream when there is an async boundary around the flow (operator)" in {
      val in = 0 to 9
      Source(in)
        .via(snitchFlow)
        .groupBy(in.size, identity)
        .via(snitchFlow)
        .async
        .mergeSubstreams
        .runWith(Sink.seq)
        .futureValue
        .sorted should ===(in)
      val refs = receiveN(in.size + in.size) // each element through the first map, then the second map
      refs.toSet should have size (in.size + 1) // outer/main actor + 1 actor per subflow
    }

    // an UnfoldResourceSource equivalent without an async boundary
    case class UnfoldResourceNoAsyncBoundary[R, T](create: () => R, readData: (R) => Option[T], close: (R) => Unit)
        extends GraphStage[SourceShape[T]] {
      val stage_ = new UnfoldResourceSource(create, readData, close)
      override def initialAttributes: Attributes = Attributes.none
      override val shape = stage_.shape
      def createLogic(inheritedAttributes: Attributes) = stage_.createLogic(inheritedAttributes)
      def asSource = Source.fromGraph(this)
    }

    "propagate downstream errors through async boundary" in {
      val promise = Promise[Done]()
      val slowInitSrc = UnfoldResourceNoAsyncBoundary(
        () => { Await.result(promise.future, 1.minute); () },
        (_: Unit) => Some(1),
        (_: Unit) => ()).asSource.watchTermination(Keep.right).async // commenting this out, makes the test pass
      val downstream = Flow[Int]
        .prepend(Source.single(1))
        .flatMapPrefix(0) {
          case Nil        => throw TE("I hate mondays")
          case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
        }
        .watchTermination(Keep.right)
        .to(Sink.ignore)

      val g = slowInitSrc.toMat(downstream)(Keep.both)

      val (f1, f2) = g.run()
      f2.failed.futureValue shouldEqual TE("I hate mondays")
      f1.value should be(empty)
      // by now downstream managed to fail, hence it already processed the message from Flow.single,
      // hence we know for sure that all graph stage locics in the downstream interpreter were initialized(=preStart)
      // hence upstream subscription was initiated.
      // since we're still blocking upstream's preStart we know for sure it didn't respond to the subscription request
      // since a blocked actor can not process additional messages from its inbox.
      // so long story short: downstream was able to initialize, subscribe and fail before upstream responded to the subscription request.
      // prior to akka#29194, this scenario resulted with cancellation signal rather than the expected error signal.
      promise.success(Done)
      f1.failed.futureValue shouldEqual TE("I hate mondays")
    }

    "propagate 'parallel' errors through async boundary via a common downstream" in {
      val promise = Promise[Done]()
      val slowInitSrc = UnfoldResourceNoAsyncBoundary(
        () => { Await.result(promise.future, 1.minute); () },
        (_: Unit) => Some(1),
        (_: Unit) => ()).asSource.watchTermination(Keep.right).async // commenting this out, makes the test pass

      val failingSrc = Source.failed(TE("I hate mondays")).watchTermination(Keep.right)

      val g = slowInitSrc.zipMat(failingSrc)(Keep.both).to(Sink.ignore)

      val (f1, f2) = g.run()
      f2.failed.futureValue shouldEqual TE("I hate mondays")
      f1.value should be(empty)
      // by now downstream managed to fail, hence it already processed the message from Flow.single,
      // hence we know for sure that all graph stage locics in the downstream interpreter were initialized(=preStart)
      // hence upstream subscription was initiated.
      // since we're still blocking upstream's preStart we know for sure it didn't respond to the subscription request
      // since a blocked actor can not process additional messages from its inbox.
      // so long story short: downstream was able to initialize, subscribe and fail before upstream responded to the subscription request.
      // prior to akka#29194, this scenario resulted with cancellation signal rather than the expected error signal.
      promise.success(Done)
      f1.failed.futureValue shouldEqual TE("I hate mondays")
    }

  }

}

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

package org.apache.pekko.stream.scaladsl

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.{ ActorRef, ActorSystem, Kill, PoisonPill, Status }
import pekko.stream.{ OverflowStrategy, _ }
import pekko.stream.testkit._
import pekko.stream.testkit.Utils._
import pekko.stream.testkit.scaladsl._

import org.reactivestreams.Publisher

@nowarn("msg=deprecated")
class ActorRefSourceSpec extends StreamSpec {

  "A ActorRefSource" must {

    "emit received messages to the stream" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef(PartialFunction.empty, PartialFunction.empty, 10, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      sub.request(2)
      ref ! 1
      s.expectNext(1)
      ref ! 2
      s.expectNext(2)
      ref ! 3
      s.expectNoMessage(500.millis)
    }

    "buffer when needed" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef(PartialFunction.empty, PartialFunction.empty, 100, OverflowStrategy.dropHead)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      for (n <- 1 to 20) ref ! n
      sub.request(10)
      for (n <- 1 to 10) s.expectNext(n)
      sub.request(10)
      for (n <- 11 to 20) s.expectNext(n)

      for (n <- 200 to 399) ref ! n
      sub.request(100)
      for (n <- 300 to 399) s.expectNext(n)
    }

    "terminate when the stream is cancelled" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef(PartialFunction.empty, PartialFunction.empty, 0, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      watch(ref)
      val sub = s.expectSubscription()
      sub.cancel()
      expectTerminated(ref)
    }

    "not fail when 0 buffer space and demand is signalled" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef(PartialFunction.empty, PartialFunction.empty, 0, OverflowStrategy.dropHead)
        .to(Sink.fromSubscriber(s))
        .run()
      watch(ref)
      val sub = s.expectSubscription()
      sub.request(100)
      sub.cancel()
      expectTerminated(ref)
    }

    "signal buffered elements and complete the stream after receiving Status.Success" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 3, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      ref ! 1
      ref ! 2
      ref ! 3
      ref ! "ok"
      sub.request(10)
      s.expectNext(1, 2, 3)
      s.expectComplete()
    }

    "signal buffered elements and complete the stream after receiving a Status.Success companion" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 3, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      ref ! 1
      ref ! 2
      ref ! 3
      ref ! "ok"
      sub.request(10)
      s.expectNext(1, 2, 3)
      s.expectComplete()
    }

    "signal buffered elements and complete the stream after receiving a Status.Success with CompletionStrategy.Draining" in {
      val (ref, s) = Source
        .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 100, OverflowStrategy.fail)
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      for (n <- 1 to 20) ref ! n
      ref ! "ok"

      s.request(10)
      for (n <- 1 to 10) s.expectNext(n)
      s.expectNoMessage(20.millis)
      s.request(10)
      for (n <- 11 to 20) s.expectNext(n)
      s.expectComplete()
    }

    "not signal buffered elements but complete immediately the stream after receiving a Status.Success with CompletionStrategy.Immediately" in {
      val (ref, s) = Source
        .actorRef({ case "ok" => CompletionStrategy.immediately }, PartialFunction.empty, 100, OverflowStrategy.fail)
        .toMat(TestSink[Int]())(Keep.both)
        .run()

      for (n <- 1 to 20) ref ! n
      ref ! "ok"

      s.request(10)

      def verifyNext(n: Int): Unit = {
        if (n > 10)
          s.expectComplete()
        else
          s.expectNextOrComplete() match {
            case Right(`n`) => verifyNext(n + 1)
            case Right(x)   => fail(s"expected $n, got $x")
            case Left(_)    => // ok, completed
          }
      }
      verifyNext(1)
    }

    "not buffer elements after receiving Status.Success" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 3, OverflowStrategy.dropBuffer)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      ref ! 1
      ref ! 2
      ref ! 3
      ref ! "ok"
      ref ! 100
      ref ! 100
      ref ! 100
      sub.request(10)
      s.expectNext(1, 2, 3)
      s.expectComplete()
    }

    "complete and materialize the stream after receiving completion message" in {
      val (ref, done) = {
        Source
          .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 3, OverflowStrategy.dropBuffer)
          .toMat(Sink.ignore)(Keep.both)
          .run()
      }
      ref ! "ok"
      done.futureValue should be(Done)
    }

    "fail the stream when receiving failure message" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef(PartialFunction.empty, { case Status.Failure(exc) => exc }, 10, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      s.expectSubscription()
      val exc = TE("testfailure")
      ref ! Status.Failure(exc)
      s.expectError(exc)
    }

    "set actor name equal to stage name" in {
      val s = TestSubscriber.manualProbe[Int]()
      val name = "SomeCustomName"
      val ref = Source
        .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 10, OverflowStrategy.fail)
        .withAttributes(Attributes.name(name))
        .to(Sink.fromSubscriber(s))
        .run()
      ref.path.name.contains(name) should ===(true)
      ref ! "ok"
    }

    "be possible to run immediately, reproducer of #26714" in {
      (1 to 100).foreach { _ =>
        val mat = Materializer(system)
        val source: Source[String, ActorRef] =
          Source.actorRef[String](PartialFunction.empty, PartialFunction.empty, 10000, OverflowStrategy.fail)
        val (_: ActorRef, _: Publisher[String]) =
          source.toMat(Sink.asPublisher(false))(Keep.both).run()(mat)
        mat.shutdown()
      }
    }

    "materialize even when the stream supervisor is not yet started" in {
      // Regression: the first Source.actorRef materialization on a fresh ActorSystem
      // can race the async supervisor startup, hitting an UnstartedCell in StageActor.localCell.
      // The fix force-starts the supervisor via Ask(Identify) before returning the cell.
      (1 to 5).foreach { i =>
        val sys = ActorSystem(s"ActorRefSource-startup-race-$i")
        try {
          val mat = Materializer(sys)
          val (ref, done) = Source
            .actorRef[Int]({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 8,
              OverflowStrategy.fail)
            .toMat(Sink.ignore)(Keep.both)
            .run()(mat)
          ref should not be null
          ref ! "ok"
          Await.result(done, 5.seconds) should be(Done)
        } finally {
          Await.result(sys.terminate(), 10.seconds)
        }
      }
    }
    "push directly without buffer round-trip when buffer is empty and demand exists" in {
      val s = TestSubscriber.manualProbe[Int]()
      val ref = Source
        .actorRef({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 100, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      // Request enough demand upfront so every element hits the direct-push fast path
      sub.request(10)
      // Send elements one at a time — each should push directly without buffering
      for (n <- 1 to 10) {
        ref ! n
        s.expectNext(n)
      }
      ref ! "ok"
      s.expectComplete()
    }

    "ignore PoisonPill and not emit it as a data element" in {
      val s = TestSubscriber.manualProbe[Any]()
      val ref = Source
        .actorRef[Any]({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 10, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      sub.request(10)
      ref ! PoisonPill
      // PoisonPill must not be emitted and must not complete the stream
      s.expectNoMessage(300.millis)
      ref ! "real-element"
      s.expectNext("real-element")
      ref ! "ok"
      s.expectComplete()
    }

    "ignore Kill and not emit it as a data element" in {
      val s = TestSubscriber.manualProbe[Any]()
      val ref = Source
        .actorRef[Any]({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 10, OverflowStrategy.fail)
        .to(Sink.fromSubscriber(s))
        .run()
      val sub = s.expectSubscription()
      sub.request(10)
      ref ! Kill
      // Kill must not be emitted and must not complete the stream
      s.expectNoMessage(300.millis)
      ref ! "real-element"
      s.expectNext("real-element")
      ref ! "ok"
      s.expectComplete()
    }

    "not fail when messages are sent after stream completion" in {
      val (ref, done) = Source
        .actorRef[Int]({ case "ok" => CompletionStrategy.draining }, PartialFunction.empty, 10, OverflowStrategy.fail)
        .toMat(Sink.ignore)(Keep.both)
        .run()
      ref ! "ok"
      Await.result(done, 5.seconds) should be(Done)
      // After completion, FunctionRef should be removed; messages go to dead letters
      ref ! 42
      ref ! 43
      // No exception should be thrown
    }
  }
}

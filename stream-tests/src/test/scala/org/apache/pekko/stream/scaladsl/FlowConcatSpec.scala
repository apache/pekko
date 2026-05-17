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

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.testkit.BaseTwoStreamsSetup
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.StreamTestKit._
import pekko.stream.testkit.scaladsl.TestSink

import org.reactivestreams.Publisher

import org.scalatest.concurrent.ScalaFutures

abstract class AbstractFlowConcatSpec extends BaseTwoStreamsSetup {

  override type Outputs = Int

  def eager: Boolean

  // not used but we want the rest of the BaseTwoStreamsSetup infra
  override def setup(p1: Publisher[Int], p2: Publisher[Int]): TestSubscriber.Probe[Int] = {
    val subscriber = TestSubscriber.probe[Outputs]()
    val s1 = Source.fromPublisher(p1)
    val s2 = Source.fromPublisher(p2)
    (if (eager) s1.concat(s2) else s1.concatLazy(s2)).runWith(Sink.fromSubscriber(subscriber))
    subscriber
  }

  s"${if (eager) "An eager" else "A lazy"} Concat for Flow " must {

    "be able to concat Flow with a Source" in {
      val f1: Flow[Int, String, _] = Flow[Int].map(_.toString + "-s")
      val s1: Source[Int, _] = Source(List(1, 2, 3))
      val s2: Source[String, _] = Source(List(4, 5, 6)).map(_.toString + "-s")

      val subs = TestSubscriber.manualProbe[Any]()
      val subSink = Sink.asPublisher[Any](false)

      val (_, res) =
        (if (eager) f1.concatLazy(s2) else f1.concat(s2)).runWith(s1, subSink)

      res.subscribe(subs)
      val sub = subs.expectSubscription()
      sub.request(9)
      (1 to 6).foreach(e => subs.expectNext(e.toString + "-s"))
      subs.expectComplete()
    }

    "be able to prepend a Source to a Flow" in {
      val s1: Source[String, _] = Source(List(1, 2, 3)).map(_.toString + "-s")
      val s2: Source[Int, _] = Source(List(4, 5, 6))
      val f2: Flow[Int, String, _] = Flow[Int].map(_.toString + "-s")

      val subs = TestSubscriber.manualProbe[Any]()
      val subSink = Sink.asPublisher[Any](false)

      val (_, res) =
        (if (eager) f2.prepend(s1) else f2.prependLazy(s1)).runWith(s2, subSink)

      res.subscribe(subs)
      val sub = subs.expectSubscription()
      sub.request(9)
      (1 to 6).foreach(e => subs.expectNext(e.toString + "-s"))
      subs.expectComplete()
    }

    commonTests()

    "work with one immediately completed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(completedPublisher, nonemptyPublisher(1 to 4))
      val subscription1 = subscriber1.expectSubscription()
      subscription1.request(5)
      (1 to 4).foreach(subscriber1.expectNext)
      subscriber1.expectComplete()

      val subscriber2 = setup(nonemptyPublisher(1 to 4), completedPublisher)
      val subscription2 = subscriber2.expectSubscription()
      subscription2.request(5)
      (1 to 4).foreach(subscriber2.expectNext)
      subscriber2.expectComplete()
    }

    "work with one delayed completed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(soonToCompletePublisher, nonemptyPublisher(1 to 4))
      val subscription1 = subscriber1.expectSubscription()
      subscription1.request(5)
      (1 to 4).foreach(subscriber1.expectNext)
      subscriber1.expectComplete()

      val subscriber2 = setup(nonemptyPublisher(1 to 4), soonToCompletePublisher)
      val subscription2 = subscriber2.expectSubscription()
      subscription2.request(5)
      (1 to 4).foreach(subscriber2.expectNext)
      subscriber2.expectComplete()
    }

    "work with one immediately failed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(failedPublisher, nonemptyPublisher(1 to 4))
      subscriber1.expectSubscriptionAndError(TestException)
    }

    "work with one nonempty and one immediately failed publisher" in assertAllStagesStopped {
      val subscriber = setup(nonemptyPublisher(1 to 4), failedPublisher)
      subscriber.expectSubscription().request(5)

      val errorSignalled = (1 to 4).foldLeft(false)((errorSignalled, e) =>
        if (!errorSignalled) subscriber.expectNextOrError(e, TestException).isLeft else true)
      if (!errorSignalled) subscriber.expectSubscriptionAndError(TestException)
    }

    "work with one delayed failed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber = setup(soonToFailPublisher, nonemptyPublisher(1 to 4))
      subscriber.expectSubscriptionAndError(TestException)
    }

    "work with one nonempty and one delayed failed publisher" in assertAllStagesStopped {
      val subscriber = setup(nonemptyPublisher(1 to 4), soonToFailPublisher)
      subscriber.expectSubscription().request(5)

      val errorSignalled = (1 to 4).foldLeft(false)((errorSignalled, e) =>
        if (!errorSignalled) subscriber.expectNextOrError(e, TestException).isLeft else true)
      if (!errorSignalled) subscriber.expectSubscriptionAndError(TestException)
    }

    "correctly handle async errors in secondary upstream" in assertAllStagesStopped {
      val promise = Promise[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()
      val s1 = Source(List(1, 2, 3))
      val s2 = Source.future(promise.future)

      (if (eager) s1.concat(s2) else s1.concatLazy(s2)).runWith(Sink.fromSubscriber(subscriber))

      val subscription = subscriber.expectSubscription()
      subscription.request(4)
      (1 to 3).foreach(subscriber.expectNext)
      promise.failure(TestException)
      subscriber.expectError(TestException)
    }

    "work with Source DSL" in {
      val s1 = Source(1 to 5)
      val s2 = Source(6 to 10)
      val testSource = (if (eager) s1.concatMat(s2)(Keep.both) else s1.concatLazyMat(s2)(Keep.both)).grouped(1000)
      Await.result(testSource.runWith(Sink.head), 3.seconds) should ===(1 to 10)

      val runnable = testSource.toMat(Sink.ignore)(Keep.left)
      val (m1, m2) = runnable.run()
      m1.isInstanceOf[NotUsed] should be(true)
      m2.isInstanceOf[NotUsed] should be(true)

      runnable.mapMaterializedValue(_ => "boo").run() should be("boo")
    }

    "work with Flow DSL" in {
      val s1 = Source(1 to 5)
      val s2 = Source(6 to 10)
      val testFlow: Flow[Int, Seq[Int], (NotUsed, NotUsed)] =
        (if (eager) Flow[Int].concatMat(s2)(Keep.both) else Flow[Int].concatLazyMat(s2)(Keep.both)).grouped(1000)
      Await.result(s1.viaMat(testFlow)(Keep.both).runWith(Sink.head), 3.seconds) should ===(1 to 10)

      val runnable = Source(1 to 5).viaMat(testFlow)(Keep.both).to(Sink.ignore)
      val x = runnable.run()
      val (m1, (m2, m3)) = x
      m1.isInstanceOf[NotUsed] should be(true)
      m2.isInstanceOf[NotUsed] should be(true)
      m3.isInstanceOf[NotUsed] should be(true)

      runnable.mapMaterializedValue(_ => "boo").run() should be("boo")
    }

    "work with Flow DSL2" in {
      val s1 = Source(1 to 5)
      val s2 = Source(6 to 10)
      val testFlow =
        (if (eager) Flow[Int].concatMat(s2)(Keep.both)
         else Flow[Int].concatLazyMat(s2)(Keep.both)).grouped(1000)
      Await.result(s1.viaMat(testFlow)(Keep.both).runWith(Sink.head), 3.seconds) should ===(1 to 10)

      val sink = testFlow.concatMat(Source(1 to 5))(Keep.both).to(Sink.ignore).mapMaterializedValue[String] {
        case ((m1, m2), m3) =>
          m1.isInstanceOf[NotUsed] should be(true)
          m2.isInstanceOf[NotUsed] should be(true)
          m3.isInstanceOf[NotUsed] should be(true)
          "boo"
      }
      Source(10 to 15).runWith(sink) should be("boo")
    }

    "subscribe at once to initial source and to one that it's concat to" in {
      val publisher1 = TestPublisher.probe[Int]()
      val publisher2 = TestPublisher.probe[Int]()
      val s1 = Source.fromPublisher(publisher1)
      val s2 = Source.fromPublisher(publisher2)
      val probeSink =
        (if (eager) s1.concat(s2) else s1.concatLazy(s2)).runWith(TestSink[Int]())

      val sub1 = publisher1.expectSubscription()
      val sub2 = publisher2.expectSubscription()
      val subSink = probeSink.expectSubscription()

      sub1.sendNext(1)
      subSink.request(1)
      probeSink.expectNext(1)
      sub1.sendComplete()

      sub2.sendNext(2)
      subSink.request(1)
      probeSink.expectNext(2)
      sub2.sendComplete()

      probeSink.expectComplete()
    }
    "optimize away empty concat" in {
      val s1 = Source.single(1)
      val concat = if (eager) s1.concat(Source.empty) else s1.concatLazy(Source.empty)
      (concat should be).theSameInstanceAs(s1)
      concat.runWith(Sink.seq).futureValue should ===(Seq(1))
    }

    "optimize single elem concat" in {
      val s1 = Source.single(1)
      val s2 = Source.single(2)
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      // avoids digging too deap into the traversal builder
      concat.traversalBuilder.pendingBuilder.toString should include("SingleConcat(2)")

      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2))
    }

    "optimize iterable concat" in {
      val s1 = Source.single(1)
      val s2 = Source(List(2, 3, 4))
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("IterableConcat")
      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2, 3, 4))
    }

    "optimize range concat" in {
      val s1 = Source.single(1)
      val s2 = Source(2 to 4)
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("IterableConcat")
      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2, 3, 4))
    }

    "optimize iterator concat" in {
      val s1 = Source.single(1)
      val s2 = Source.fromIterator(() => Iterator(2, 3, 4))
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("IterableConcat")
      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2, 3, 4))
    }

    "optimize java-stream concat" in {
      val s1 = Source.single(1)
      val s2 = Source.fromJavaStream(() => Collections.singleton(2: Integer).stream()).map(_.intValue())
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      // map() is not value-presented, so the optimization should not kick in for s2 here.
      // To exercise the optimization, build a JavaStream source whose value-presented form survives.
      val s2Direct = Source.fromJavaStream(() => Collections.singleton(2: Integer).stream())
      val concatDirect: Source[Integer, _] =
        if (eager) Source.single[Integer](1).concat(s2Direct) else Source.single[Integer](1).concatLazy(s2Direct)
      concatDirect.traversalBuilder.pendingBuilder.toString should include("IterableConcat")

      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2))
    }

    "optimize repeat concat" in {
      val s1 = Source(1 to 3)
      val s2 = Source.repeat(0)
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("RepeatConcat(0)")
      concat.take(6).runWith(Sink.seq).futureValue should ===(Seq(1, 2, 3, 0, 0, 0))
    }

    "optimize failed concat" in {
      val ex = new RuntimeException("boom") with NoStackTrace
      val s1 = Source.single(1)
      val s2: Source[Int, NotUsed] = Source.failed(ex)
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("FailedConcat")
      concat.runWith(Sink.seq).failed.futureValue should ===(ex)
    }

    "optimize completed-future concat" in {
      // `Source.future(Future.successful(x))` is itself optimized to a `SingleSource`,
      // so the dispatch lands on `SingleConcat` rather than `FutureConcat`.
      val s1 = Source.single(1)
      val s2 = Source.future(Future.successful(2))
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("SingleConcat(2)")
      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2))
    }

    "optimize pending-future concat" in {
      val promise = Promise[Int]()
      val s1 = Source.single(1)
      val s2 = Source.future(promise.future)
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("FutureConcat")
      val resultF = concat.runWith(Sink.seq)
      promise.success(2)
      resultF.futureValue should ===(Seq(1, 2))
    }

    "optimize failed-future concat" in {
      // `Source.future(Future.failed(ex))` is itself optimized to a `FailedSource`,
      // so the dispatch lands on `FailedConcat` rather than `FutureConcat`.
      val ex = new RuntimeException("future-boom") with NoStackTrace
      val s1 = Source.single(1)
      val s2 = Source.future(Future.failed[Int](ex))
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.traversalBuilder.pendingBuilder.toString should include("FailedConcat")
      concat.runWith(Sink.seq).failed.futureValue should ===(ex)
    }

    "avoid downstream substream materialization for value-presented sources" in {
      // Wrap each emitted element through a counting map to check that no inner-source materialization fires.
      // (For value-presented sources, the optimization avoids spinning up substreams.)
      val materializationCounter = new AtomicInteger(0)
      val s1 = Source.single(1).map { v => materializationCounter.incrementAndGet(); v }
      val s2 = Source(2 to 4)
      val concat = if (eager) s1.concat(s2) else s1.concatLazy(s2)

      concat.runWith(Sink.seq).futureValue should ===(Seq(1, 2, 3, 4))
      materializationCounter.get() should ===(1) // one for the upstream s1 element only
    }
  }
}

class FlowConcatSpec extends AbstractFlowConcatSpec with ScalaFutures {
  override def eager: Boolean = true

  "concat" must {
    "work in example" in {
      // #concat

      val sourceA = Source(List(1, 2, 3, 4))
      val sourceB = Source(List(10, 20, 30, 40))

      sourceA.concat(sourceB).runWith(Sink.foreach(println))
      // prints 1, 2, 3, 4, 10, 20, 30, 40
      // #concat
    }
  }
}

class FlowConcatLazySpec extends AbstractFlowConcatSpec {
  override def eager: Boolean = false

  "concatLazy" must {
    "Make it possible to entirely avoid materialization of the second flow" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.probe[Int]()
      val secondStreamWasMaterialized = new AtomicBoolean(false)
      Source
        .fromPublisher(publisher)
        .concatLazy(Source.lazySource { () =>
          secondStreamWasMaterialized.set(true)
          Source.single(3)
        })
        .runWith(Sink.fromSubscriber(subscriber))
      subscriber.request(1)
      publisher.sendNext(1)
      subscriber.expectNext(1)
      subscriber.cancel()
      publisher.expectCancellation()
      // cancellation went all the way upstream across one async boundary so if second source materialization
      // would happen it would have happened already
      secondStreamWasMaterialized.get should ===(false)
    }

    "work in example" in {
      // #concatLazy
      val sourceA = Source(List(1, 2, 3, 4))
      val sourceB = Source(List(10, 20, 30, 40))

      sourceA.concatLazy(sourceB).runWith(Sink.foreach(println))
      // #concatLazy
    }
  }

}

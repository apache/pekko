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
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.stream._
import pekko.stream.ActorAttributes.supervisionStrategy
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.testkit.DefaultTimeout

import org.reactivestreams.Publisher

import org.scalatest.concurrent.ScalaFutures

class SinkSpec extends StreamSpec with DefaultTimeout with ScalaFutures {

  import GraphDSL.Implicits._

  "A Sink" must {
    "be composable without importing modules" in {
      val probes = Array.fill(3)(TestSubscriber.manualProbe[Int]())
      val sink = Sink.fromGraph(GraphDSL.create() { implicit b =>
        val bcast = b.add(Broadcast[Int](3))
        for (i <- 0 to 2) bcast.out(i).filter(_ == i) ~> Sink.fromSubscriber(probes(i))
        SinkShape(bcast.in)
      })
      Source(List(0, 1, 2)).runWith(sink)

      val subscriptions = probes.map(_.expectSubscription())
      subscriptions.foreach { s =>
        s.request(3)
      }
      probes.zipWithIndex.foreach { case (p, i) => p.expectNext(i) }
      probes.foreach { case p => p.expectComplete() }
    }

    "be composable with importing 1 module" in {
      val probes = Array.fill(3)(TestSubscriber.manualProbe[Int]())
      val sink = Sink.fromGraph(GraphDSL.createGraph(Sink.fromSubscriber(probes(0))) { implicit b => s0 =>
        val bcast = b.add(Broadcast[Int](3))
        bcast.out(0)                                  ~> Flow[Int].filter(_ == 0) ~> s0.in
        for (i <- 1 to 2) bcast.out(i).filter(_ == i) ~> Sink.fromSubscriber(probes(i))
        SinkShape(bcast.in)
      })
      Source(List(0, 1, 2)).runWith(sink)

      val subscriptions = probes.map(_.expectSubscription())
      subscriptions.foreach { s =>
        s.request(3)
      }
      probes.zipWithIndex.foreach { case (p, i) => p.expectNext(i) }
      probes.foreach { case p => p.expectComplete() }
    }

    "be composable with importing 2 modules" in {
      val probes = Array.fill(3)(TestSubscriber.manualProbe[Int]())
      val sink =
        Sink.fromGraph(
          GraphDSL.createGraph(Sink.fromSubscriber(probes(0)), Sink.fromSubscriber(probes(1)))(List(_, _)) {
            implicit b => (s0, s1) =>
              val bcast = b.add(Broadcast[Int](3))
              bcast.out(0).filter(_ == 0) ~> s0.in
              bcast.out(1).filter(_ == 1) ~> s1.in
              bcast.out(2).filter(_ == 2) ~> Sink.fromSubscriber(probes(2))
              SinkShape(bcast.in)
          })
      Source(List(0, 1, 2)).runWith(sink)

      val subscriptions = probes.map(_.expectSubscription())
      subscriptions.foreach { s =>
        s.request(3)
      }
      probes.zipWithIndex.foreach { case (p, i) => p.expectNext(i) }
      probes.foreach { case p => p.expectComplete() }
    }

    "be composable with importing 3 modules" in {
      val probes = Array.fill(3)(TestSubscriber.manualProbe[Int]())
      val sink = Sink.fromGraph(
        GraphDSL.createGraph(
          Sink.fromSubscriber(probes(0)),
          Sink.fromSubscriber(probes(1)),
          Sink.fromSubscriber(probes(2)))(List(_, _, _)) { implicit b => (s0, s1, s2) =>
          val bcast = b.add(Broadcast[Int](3))
          bcast.out(0).filter(_ == 0) ~> s0.in
          bcast.out(1).filter(_ == 1) ~> s1.in
          bcast.out(2).filter(_ == 2) ~> s2.in
          SinkShape(bcast.in)
        })
      Source(List(0, 1, 2)).runWith(sink)

      val subscriptions = probes.map(_.expectSubscription())
      subscriptions.foreach { s =>
        s.request(3)
      }
      probes.zipWithIndex.foreach { case (p, i) => p.expectNext(i) }
      probes.foreach { case p => p.expectComplete() }
    }

    "combine to many outputs with simplified API" in {
      val probes = Seq.fill(3)(TestSubscriber.manualProbe[Int]())
      val sink =
        Sink.combine(Sink.fromSubscriber(probes(0)), Sink.fromSubscriber(probes(1)), Sink.fromSubscriber(probes(2)))(
          Broadcast[Int](_))

      Source(List(0, 1, 2)).runWith(sink)

      val subscriptions = probes.map(_.expectSubscription())

      subscriptions.foreach { s =>
        s.request(1)
      }
      probes.foreach { p =>
        p.expectNext(0)
      }

      subscriptions.foreach { s =>
        s.request(2)
      }
      probes.foreach { p =>
        p.expectNextN(List(1, 2))
        p.expectComplete()
      }
    }

    "combine many sinks to one" in {
      val source = Source(List(0, 1, 2, 3, 4, 5))
      implicit val ex = scala.concurrent.ExecutionContext.parasitic
      val sink = Sink
        .combine(
          List(
            Sink.reduce[Int]((a, b) => a + b),
            Sink.reduce[Int]((a, b) => a + b),
            Sink.reduce[Int]((a, b) => a + b)))(Broadcast[Int](_))
        .mapMaterializedValue(Future.reduceLeft(_)(_ + _))
      val result = source.runWith(sink)
      result.futureValue should be(45)
    }

    "combine two sinks with combineMat" in {
      implicit val ex = scala.concurrent.ExecutionContext.parasitic
      Source(List(0, 1, 2, 3, 4, 5))
        .toMat(Sink.combineMat(Sink.reduce[Int]((a, b) => a + b), Sink.reduce[Int]((a, b) => a + b))(Broadcast[Int](_))(
          (f1, f2) => {
            for {
              r1 <- f1
              r2 <- f2
            } yield r1 + r2
          }))(Keep.right)
        .run()
        .futureValue should be(30)
    }

    "combine to two sinks with simplified API" in {
      val probes = Seq.fill(2)(TestSubscriber.manualProbe[Int]())
      val sink = Sink.combine(Sink.fromSubscriber(probes(0)), Sink.fromSubscriber(probes(1)))(Broadcast[Int](_))

      Source(List(0, 1, 2)).runWith(sink)

      val subscriptions = probes.map(_.expectSubscription())

      subscriptions.foreach { s =>
        s.request(1)
      }
      probes.foreach { p =>
        p.expectNext(0)
      }

      subscriptions.foreach { s =>
        s.request(2)
      }
      probes.foreach { p =>
        p.expectNextN(List(1, 2))
        p.expectComplete()
      }
    }

    "suitably override attribute handling methods" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].async.addAttributes(none).named("name")

      s.traversalBuilder.attributes.filtered[Name] shouldEqual List(Name("name"), Name("headSink"))
      @nowarn("msg=deprecated")
      val res = s.traversalBuilder.attributes.getFirst[Attributes.AsyncBoundary.type]
      res shouldEqual (Some(AsyncBoundary))
    }

    "given one attribute of a class should correctly get it as first attribute with default value" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].async.addAttributes(none).named("name")

      s.traversalBuilder.attributes.filtered[Name] shouldEqual List(Name("name"), Name("headSink"))
    }

    "given one attribute of a class should correctly get it as last attribute with default value" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].async.addAttributes(none).named("name")

      s.traversalBuilder.attributes.get[Name](Name("default")) shouldEqual Name("name")
    }

    "given no attributes of a class when getting first attribute with default value should get default value" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].withAttributes(none).async

      @nowarn("msg=deprecated")
      val res = s.traversalBuilder.attributes.getFirst[Name](Name("default"))
      res shouldEqual Name("default")
    }

    "given no attributes of a class when getting last attribute with default value should get default value" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].withAttributes(none).async

      s.traversalBuilder.attributes.get[Name](Name("default")) shouldEqual Name("default")
    }

    "given multiple attributes of a class when getting first attribute with default value should get first attribute" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].withAttributes(none).async.named("name").named("another_name")
      @nowarn("msg=deprecated")
      val res = s.traversalBuilder.attributes.getFirst[Name](Name("default"))
      res shouldEqual Name("name")
    }

    "given multiple attributes of a class when getting last attribute with default value should get last attribute" in {
      import Attributes._
      val s: Sink[Int, Future[Int]] = Sink.head[Int].async.addAttributes(none).named("name").named("another_name")

      s.traversalBuilder.attributes.get[Name](Name("default")) shouldEqual Name("another_name")
    }

    "support contramap" in {
      Source(0 to 9).toMat(Sink.seq.contramap(_ + 1))(Keep.right).run().futureValue should ===(1 to 10)
    }
  }

  "The ignore sink" should {

    "fail its materialized value on abrupt materializer termination" in {
      @nowarn("msg=deprecated")
      val mat = ActorMaterializer()

      val matVal = Source.maybe[Int].runWith(Sink.ignore)(mat)

      mat.shutdown()

      matVal.failed.futureValue shouldBe a[AbruptStageTerminationException]
    }
  }

  "The never sink" should {

    "always backpressure" in {
      val (source, doneFuture) = TestSource.probe[Int].toMat(Sink.never)(Keep.both).run()
      source.ensureSubscription()
      source.expectRequest()
      source.sendComplete()
      Await.result(doneFuture, 100.millis) should ===(Done)
    }

    "can failed with upstream failure" in {
      val (source, doneFuture) = TestSource.probe[Int].toMat(Sink.never)(Keep.both).run()
      source.ensureSubscription()
      source.expectRequest()
      source.sendError(new RuntimeException("Oops"))
      a[RuntimeException] shouldBe thrownBy {
        Await.result(doneFuture, 100.millis)
      }
    }

    "fail its materialized value on abrupt materializer termination" in {
      @nowarn("msg=deprecated")
      val mat = ActorMaterializer()
      val matVal = Source.single(1).runWith(Sink.never)(mat)
      mat.shutdown()
      matVal.failed.futureValue shouldBe a[AbruptStageTerminationException]
    }
  }

  "The reduce sink" must {
    "sum up 1 to 10 correctly" in {
      // #reduce-operator-example
      val source = Source(1 to 10)
      val result = source.runWith(Sink.reduce[Int]((a, b) => a + b))
      result.map(println)(system.dispatcher)
      // will print
      // 55
      // #reduce-operator-example
      assert(result.futureValue == (1 to 10).sum)
    }
  }

  "The seq sink" must {
    "collect the streamed elements into a sequence" in {
      // #seq-operator-example
      val source = Source(1 to 3)
      val result = source.runWith(Sink.seq[Int])
      val seq = result.futureValue
      seq.foreach(println)
      // will print
      // 1
      // 2
      // 3
      // #seq-operator-example
      assert(seq == Vector(1, 2, 3))
    }
  }

  "The count sink" must {
    "count the number of elements in the stream" in {
      // #count-operator-example
      val source = Source(1 to 10)
      val result = source.runWith(Sink.count)
      val count = result.futureValue
      println(count)
      // will print
      // 10
      // #count-operator-example
      assert(result.futureValue == 10)
    }
  }

  "The foreach sink" must {
    "illustrate println" in {
      // #foreach
      val printlnSink: Sink[Any, Future[Done]] = Sink.foreach(println)
      val f = Source(1 to 4).runWith(printlnSink)
      val done = Await.result(f, 100.millis)
      // will print
      // 1
      // 2
      // 3
      // 4
      // #foreach
      done shouldBe Done
    }
  }

  "The forall sink" must {

    "completes with `ture` when all elements match" in {
      Source(1 to 4)
        .runWith(Sink.forall(_ > 0))
        .futureValue shouldBe true
    }

    "completes with `false` when any element match" in {
      Source(1 to 4)
        .runWith(Sink.forall(_ > 2))
        .futureValue shouldBe false
    }

    "completes with `true` if the stream is empty" in {
      Source.empty[Int]
        .runWith(Sink.forall(_ > 2))
        .futureValue shouldBe true
    }

    "completes with `Failure` if the stream failed" in {
      Source.failed[Int](new RuntimeException("Oops"))
        .runWith(Sink.forall(_ > 2))
        .failed.futureValue shouldBe a[RuntimeException]
    }

    "completes with `true` with restart strategy" in {
      val sink = Sink.forall[Int](elem => {
        if (elem == 2) {
          throw new RuntimeException("Oops")
        }
        elem > 0
      }).withAttributes(supervisionStrategy(Supervision.restartingDecider))

      Source(1 to 2)
        .runWith(sink)
        .futureValue shouldBe true
    }

  }

  "The none sink" must {

    "completes with `ture` when all elements not match" in {
      Source(1 to 4)
        .runWith(Sink.none(_ < 0))
        .futureValue shouldBe true
    }

    "completes with `false` when any element match" in {
      Source(1 to 4)
        .runWith(Sink.none(_ > 2))
        .futureValue shouldBe false
    }

    "completes with `true` if the stream is empty" in {
      Source.empty[Int]
        .runWith(Sink.none(_ > 2))
        .futureValue shouldBe true
    }

    "completes with `Failure` if the stream failed" in {
      Source.failed[Int](new RuntimeException("Oops"))
        .runWith(Sink.none(_ > 2))
        .failed.futureValue shouldBe a[RuntimeException]
    }

    "completes with `false` with restart strategy" in {
      val sink = Sink.none[Int](elem => {
        if (elem == 2) {
          throw new RuntimeException("Oops")
        }
        elem > 1
      }).withAttributes(supervisionStrategy(Supervision.restartingDecider))

      Source(1 to 3)
        .runWith(sink)
        .futureValue shouldBe false
    }
  }

  "The exists sink" must {

    "completes with `false` when none element match" in {
      Source(1 to 4)
        .runWith(Sink.exists[Int](_ > 5))
        .futureValue shouldBe false
    }

    "completes with `true` when any element match" in {
      Source(1 to 4)
        .runWith(Sink.exists(_ > 2))
        .futureValue shouldBe true
    }

    "completes with `false` if the stream is empty" in {
      Source.empty[Int]
        .runWith(Sink.exists(_ > 2))
        .futureValue shouldBe false
    }

    "completes with `Failure` if the stream failed" in {
      Source.failed[Int](new RuntimeException("Oops"))
        .runWith(Sink.exists(_ > 2))
        .failed.futureValue shouldBe a[RuntimeException]
    }

    "completes with `exists` with restart strategy" in {
      val sink = Sink.exists[Int](elem => {
        if (elem == 2) {
          throw new RuntimeException("Oops")
        }
        elem > 1
      }).withAttributes(supervisionStrategy(Supervision.restartingDecider))

      Source(1 to 2)
        .runWith(sink)
        .futureValue shouldBe false
    }

  }

  "Sink pre-materialization" must {
    "materialize the sink and wrap its exposed publisher in a Source" in {
      val publisherSink: Sink[String, Publisher[String]] = Sink.asPublisher[String](false)
      val (matPub, sink) = publisherSink.preMaterialize()

      val probe = Source.fromPublisher(matPub).runWith(TestSink.probe)
      probe.expectNoMessage(100.millis)

      Source.single("hello").runWith(sink)

      probe.ensureSubscription()
      probe.requestNext("hello")
      probe.expectComplete()
    }
    "materialize the sink and wrap its exposed publisher(fanout) in a Source twice" in {
      val publisherSink: Sink[String, Publisher[String]] = Sink.asPublisher[String](fanout = true)
      val (matPub, sink) = publisherSink.preMaterialize()

      val probe1 = Source.fromPublisher(matPub).runWith(TestSink.probe)
      val probe2 = Source.fromPublisher(matPub).runWith(TestSink.probe)

      Source.single("hello").runWith(sink)

      probe1.ensureSubscription()
      probe1.requestNext("hello")
      probe1.expectComplete()

      probe2.ensureSubscription()
      probe2.requestNext("hello")
      probe2.expectComplete()
    }
    "materialize the sink and wrap its exposed publisher(not fanout), should fail the second materialization" in {
      val publisherSink: Sink[String, Publisher[String]] = Sink.asPublisher[String](fanout = false)
      val (matPub, sink) = publisherSink.preMaterialize()

      val probe1 = Source.fromPublisher(matPub).runWith(TestSink.probe)
      val probe2 = Source.fromPublisher(matPub).runWith(TestSink.probe)

      Source.single("hello").runWith(sink)

      probe1.ensureSubscription()
      probe1.requestNext("hello")
      probe1.expectComplete()

      probe2.ensureSubscription()
      probe2.expectError().getMessage should include("only supports one subscriber")
    }
  }

}

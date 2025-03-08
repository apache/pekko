/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.Attributes
import pekko.stream.Outlet
import pekko.stream.SourceShape
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.OutHandler
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.Utils.TE
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource

import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class FlowSwitchSpec extends StreamSpec {

  import FlowSwitchSpec._
  import system.dispatcher

  "A Switch / switchMap operator" must {

    "wait for the last substream to complete" in {
      Source(1 to 3)
        .switchMap(x => Source(1 to 3).map(x -> _))
        .runWith(Sink.seq)
        .map(_.takeRight(3))
        .futureValue should ===(Seq(3 -> 1, 3 -> 2, 3 -> 3))
    }

    "not wait for the previous / non-last substream to complete" in {
      val lastValues = List(1, 2)
      Source(List(Source.never, Source(lastValues)))
        .switchMap(identity)
        .runWith(Sink.seq)
        .futureValue should ===(lastValues)
    }

    // guards against bugs that may occur if in the future, someone applies certain Source.single-specific
    // optimizations
    "not behave differently for substreams Source.single(x) and Source(List(x))" in {
      def via(createSubStream: Int => Source[Int, NotUsed]): Seq[Int] =
        Source(0 to 3).switchMap(createSubStream).runWith(Sink.seq).futureValue

      via(Source.single) should ===(via(x => Source(List(x))))
    }

    "complete if upstream completes after last substream" in {
      val (mainPub, probe) =
        TestSource.probe[Source[Int, NotUsed]].switchMap(identity).toMat(TestSink.probe)(Keep.both).run()

      def nextSubstream(): TestPublisher.Probe[Int] = {
        val p = TestPublisher.probe[Int]()
        mainPub.sendNext(Source.fromPublisher(p))
        p
      }

      probe.request(1)

      nextSubstream().sendComplete()
      nextSubstream().sendComplete()
      probe.expectNoMessage()
      mainPub.sendComplete()
      probe.expectComplete()
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "propagate early failure from main stream" in {
      val ex = new Exception("buh")
      intercept[TestFailedException] {
        Source.failed(ex).switchMap((i: Int) => Source.single(i)).runWith(Sink.head).futureValue
      }.cause.get should ===(ex)
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "propagate late failure from main stream" in {
      val ex = new Exception("buh")
      intercept[TestFailedException] {
        (Source(List(Source.never, Source.never)) ++ Source.failed(ex)).switchMap(identity).runWith(
          Sink.head).futureValue
      }.cause.get should ===(ex)
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "propagate failure from map function" in {
      val ex = new Exception("buh")
      intercept[TestFailedException] {
        Source(1 to 3).switchMap(i => if (i == 3) throw ex else Source.never).runWith(Sink.head).futureValue
      }.cause.get should ===(ex)
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "bubble up substream exceptions" in {
      val ex = new Exception("buh")
      intercept[TestFailedException] {
        Source(List(Source.never, Source.never, Source.failed(ex))).switchMap(identity).runWith(Sink.head).futureValue
      }.cause.get should ===(ex)
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "bubble up substream materialization exception" in {
      val matFail = TE("fail!")
      object FailingInnerMat extends GraphStage[SourceShape[String]] {
        val out = Outlet[String]("out")
        val shape = SourceShape(out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
          if ("confuse IntellIJ dead code checker".length > 2) {
            throw matFail
          }
        }
      }

      val result = Source.single(()).switchMap(_ => Source.fromGraph(FailingInnerMat)).runWith(Sink.ignore)

      result.failed.futureValue should ===(matFail)

    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "cancel substream when failing from main stream" in {
      val (mainPub, probe) =
        TestSource.probe[Source[Int, NotUsed]].switchMap(identity).toMat(TestSink.probe)(Keep.both).run()
      val subPub = TestPublisher.probe[Int]()

      probe.request(1)
      mainPub.sendNext(Source.fromPublisher(subPub))
      subPub.expectRequest()

      val ex = new Exception("buh")
      mainPub.sendError(ex)
      subPub.expectCancellation()
    }

    "cancel previous substream when failing map function" in {
      val p = TestPublisher.probe[Int]()
      val ex = new Exception("buh")
      Source.tick(0.millis, 100.millis, 1).scan(1)(_ + _)
        .switchMap {
          case 1 =>
            Source.fromPublisher(p)
          case 2 =>
            throw ex
        }
        .toMat(Sink.seq)(Keep.right)
        .run()
      p.expectCancellation()
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "cancel substream when being cancelled" in {
      val p = TestPublisher.probe[Int]()
      val sink = (Source(List(Source.fromPublisher(p))) ++ Source.never)
        .switchMap(identity)
        .runWith(TestSink.probe)
      sink.request(1)
      p.expectRequest()
      sink.cancel()
      p.expectCancellation()
    }

    "cancel previous substream once a next substream arrives" in {
      val (mainPub, probe) =
        TestSource.probe[Source[Int, NotUsed]].switchMap(identity).toMat(TestSink.probe)(Keep.both).run()

      def nextSubstream(): TestPublisher.Probe[Int] = {
        val p = TestPublisher.probe[Int]()
        mainPub.sendNext(Source.fromPublisher(p))
        p
      }

      probe.request(1)

      val subPub1 = nextSubstream()
      subPub1.ensureSubscription()
      val subPub2 = nextSubstream()
      subPub1.expectCancellation()
      subPub2.ensureSubscription()
    }

    // copied (with modifications) from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
    "propagate attributes to inner streams" in {
      val f = Source
        .single(attributesSource.addAttributes(Attributes.name("inner")))
        .switchMap(identity)
        .addAttributes(Attributes.name("outer"))
        .runWith(Sink.head)

      val attributes = Await.result(f, 3.seconds).attributeList
      attributes should contain(Attributes.Name("inner"))
      attributes should contain(Attributes.Name("outer"))
      attributes.indexOf(Attributes.Name("inner")) < attributes.indexOf(Attributes.Name("outer")) should be(true)
    }

    "respect backpressure when emitting items from substream that is faster than downstream" in {
      val (mainPub, probe) =
        TestSource.probe[Source[Long, NotUsed]].switchMap(identity).toMat(TestSink.probe)(Keep.both).run()

      def nextSubstream(): TestPublisher.Probe[Long] = {
        val p = TestPublisher.probe[Long]()
        mainPub.sendNext(Source.fromPublisher(p))
        p
      }

      val subPub1 = nextSubstream()
      probe.request(1)
      val nRequests = subPub1.expectRequest()
      nRequests should be >= 1L

      for (x <- 1L to nRequests) {
        subPub1.sendNext(x)
      }

      subPub1.expectNoMessage()

      probe.expectNext(1L)
      probe.expectNoMessage()

      // end of backpressure:
      probe.request(nRequests - 1)
      for (x <- 2L to nRequests) {
        probe.expectNext(x)
      }

    }

    "never backpressure to upstream" in {
      val (mainPub, probe) =
        TestSource.probe[Source[Int, NotUsed]].switchMap(identity).toMat(TestSink.probe)(Keep.both).run()

      for (_ <- 0 until 10) {
        for (_ <- 0L until mainPub.expectRequest()) {
          mainPub.sendNext(Source.single(-1))
        }
        for (_ <- 0L until mainPub.expectRequest()) {
          mainPub.sendNext(Source.never)
        }
      }

      val requests = mainPub.expectRequest()
      mainPub.sendNext(Source(List(1, 2)))
      probe.request(1)
      probe.expectNext(1)
      probe.expectNoMessage()
      for (_ <- 0L until requests - 1) {
        mainPub.sendNext(Source.never)
      }
      probe.expectNoMessage()
      probe.request(1)
      for (_ <- 0 until 10) {
        for (_ <- 0L until mainPub.expectRequest()) {
          mainPub.sendNext(Source.never)
        }
      }
      probe.expectNoMessage()
      mainPub.sendNext(Source.single(3))
      probe.expectNext(3)
    }
  }

}

object FlowSwitchSpec {

  // copied from org.apache.pekko.stream.scaladsl.FlowFlattenMergeSpec
  private val attributesSource = Source.fromGraph(new GraphStage[SourceShape[Attributes]] {
    val out = Outlet[Attributes]("AttributesSource.out")
    override val shape: SourceShape[Attributes] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {
        override def onPull(): Unit = {
          push(out, inheritedAttributes)
          completeStage()
        }

        setHandler(out, this)
      }
  })
}

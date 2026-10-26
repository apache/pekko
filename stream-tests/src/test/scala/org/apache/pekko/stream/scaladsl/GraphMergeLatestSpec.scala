/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream._
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.StreamTestKit._
import pekko.stream.testkit.scaladsl.TestSource

class GraphMergeLatestSpec extends TwoStreamsSetup {
  import GraphDSL.Implicits._

  override type Outputs = List[Int]

  override def fixture(b: GraphDSL.Builder[?]): Fixture = new Fixture {
    val merge = b.add(MergeLatest[Int](2))

    override def left: Inlet[Int] = merge.in(0)
    override def right: Inlet[Int] = merge.in(1)
    override def out: Outlet[Outputs] = merge.out

  }

  "mergeLatest" must {

    "start emit values only after each input stream emitted value" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val up3 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, in2, in3) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2, up3)((_, _, _)) { implicit b => (s1, s2, s3) =>
          val m = b.add(MergeLatest[Int](3))

          s1    ~> m
          s2    ~> m
          s3    ~> m
          m.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()

      subscription.request(1)
      probe.expectNoMessage(10.millis)
      in1.sendNext(1)
      probe.expectNoMessage(10.millis)
      in2.sendNext(2)
      probe.expectNoMessage(10.millis)
      in3.sendNext(3)
      probe.expectNext(List(1, 2, 3))
      in1.sendComplete()
      in2.sendComplete()
      in3.sendComplete()
      probe.expectComplete()
    }

    "conflate updates while downstream is back-pressured" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, in2) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2)((_, _)) { implicit b => (s1, s2) =>
          val m = b.add(MergeLatest[Int](2))

          s1    ~> m
          s2    ~> m
          m.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()

      in1.sendNext(1)
      in2.sendNext(2)
      in1.sendNext(3)
      in2.sendNext(4)
      probe.expectNoMessage(10.millis)
      subscription.request(1)
      probe.expectNext(List(3, 4))

      in2.sendNext(5)
      in2.sendNext(6)
      subscription.request(1)
      probe.expectNext(List(3, 6))

      in1.sendComplete()
      in2.sendComplete()
      probe.expectComplete()
    }

    "emit a pending latest combination before completing" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, in2) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2)((_, _)) { implicit b => (s1, s2) =>
          val m = b.add(MergeLatest[Int](2))

          s1    ~> m
          s2    ~> m
          m.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()

      in1.sendNext(1)
      in2.sendNext(2)
      in1.sendNext(3)
      in1.sendComplete()
      in2.sendComplete()
      probe.expectNoMessage(10.millis)

      subscription.request(1)
      probe.expectNext(List(3, 2))
      probe.expectComplete()
    }

    "eagerly complete after emitting the pending latest combination" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, in2) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2)((_, _)) { implicit b => (s1, s2) =>
          val m = b.add(MergeLatest[Int](2, eagerComplete = true))

          s1    ~> m
          s2    ~> m
          m.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()

      in1.sendNext(1)
      in2.sendNext(2)
      in2.sendNext(3)
      in1.sendComplete()
      probe.expectNoMessage(10.millis)

      subscription.request(1)
      probe.expectNext(List(1, 3))
      probe.expectComplete()
    }

    "update values after message from one stream" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val up3 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, in2, in3) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2, up3)((_, _, _)) { implicit b => (s1, s2, s3) =>
          val m = b.add(MergeLatest[Int](3))

          s1    ~> m
          s2    ~> m
          s3    ~> m
          m.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()

      in1.sendNext(1)
      in2.sendNext(2)
      in3.sendNext(3)
      subscription.request(1)
      probe.expectNext() should be(List(1, 2, 3))

      in1.sendNext(2)
      subscription.request(1)
      probe.expectNext() should be(List(2, 2, 3))

      in2.sendNext(4)
      subscription.request(1)
      probe.expectNext() should be(List(2, 4, 3))

      in3.sendNext(6)
      subscription.request(1)
      probe.expectNext() should be(List(2, 4, 6))

      in3.sendNext(9)
      subscription.request(1)
      probe.expectNext() should be(List(2, 4, 9))

      in1.sendNext(4)
      subscription.request(1)
      probe.expectNext() should be(List(4, 4, 9))

      in1.sendComplete()
      in2.sendComplete()
      in3.sendComplete()
      probe.expectComplete()
    }

    "work with one-way merge" in assertAllStagesStopped {
      val upstream = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val input = RunnableGraph
        .fromGraph(GraphDSL.createGraph(upstream) { implicit b => source =>
          val merge = b.add(MergeLatest[Int](1))

          source    ~> merge
          merge.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()
      input.sendNext(1)
      input.sendNext(2)
      input.sendNext(3)
      input.sendComplete()
      probe.expectNoMessage(10.millis)

      subscription.request(1)
      probe.expectNext(List(3))
      probe.expectComplete()
    }

    "complete stage if eagerComplete is set and one of input stream finished" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, _) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2)((_, _)) { implicit b => (s1, s2) =>
          val m = b.add(MergeLatest[Int](2, true))

          s1    ~> m
          s2    ~> m
          m.out ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      probe.expectSubscription()

      in1.sendComplete()
      probe.expectComplete()
    }

    commonTests()

  }

}

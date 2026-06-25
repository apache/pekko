/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.{ ActorAttributes, Supervision }
import pekko.stream.testkit.{ ScriptedTest, StreamSpec, TestPublisher, TestSubscriber }
import pekko.testkit.TimingTest

class FlowGroupedWeightedSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A GroupedWeighted" must {
    "produce no group (empty sink sequence) when source is empty" in {
      val input = immutable.Seq.empty
      def costFn(@nowarn("msg=never used") e: Int): Long = 999999L // set to an arbitrarily big value
      val future = Source(input).groupedWeighted(1)(costFn).runWith(Sink.seq)
      val result = Await.result(future, remainingOrDefault)
      result should be(Seq.empty)
    }

    "always exhaust a source into a single group if cost is 0" in {
      val input = 1 to 15
      def costFn(@nowarn("msg=never used") e: Int): Long = 0L
      val minWeight = 1 // chose the least possible value for minWeight
      val future = Source(input).groupedWeighted(minWeight)(costFn).runWith(Sink.seq)
      val result = Await.result(future, remainingOrDefault)
      result should be(Seq(input))
    }

    "exhaust source into one group if minWeight equals the accumulated cost of the source" in {
      val input = 1 to 16
      def costFn(@nowarn("msg=never used") e: Int): Long = 1L
      val minWeight = input.length
      val future = Source(input).groupedWeighted(minWeight)(costFn).runWith(Sink.seq)
      val result = Await.result(future, remainingOrDefault)
      result should be(Seq(input))
    }

    "exhaust a source into one group if minWeight is greater than the accumulated cost of source" in {
      val input = List("this", "is", "some", "string")
      def costFn(e: String): Long = e.length
      val minWeight = Long.MaxValue
      val future = Source(input).groupedWeighted(minWeight)(costFn).runWith(Sink.seq)
      val result = Await.result(future, remainingOrDefault)
      result should be(Seq(input))
    }

    "emit a group each time the grouped weight is greater than minWeight" in {
      val input = List(1, 2, 1, 2)
      def costFn(e: Int): Long = e
      val minWeight = 2 // must produce two groups of List(1, 2)
      val future = Source(input).groupedWeighted(minWeight)(costFn).runWith(Sink.seq)
      val result: Seq[Seq[Int]] = Await.result(future, remainingOrDefault)
      result should be(Seq(Seq(1, 2), Seq(1, 2)))
    }

    "not emit group when grouped weight is less than minWeight and upstream has not completed" taggedAs TimingTest in {
      val p = TestPublisher.probe[Int]()
      val c = TestSubscriber.probe[immutable.Seq[Int]]()
      // Note that the cost function set to zero here means the stream will accumulate elements until completed
      Source.fromPublisher(p).groupedWeighted(10)(_ => 0L).to(Sink.fromSubscriber(c)).run()
      p.sendNext(1)
      c.expectSubscription().request(1) // create downstream demand so Grouped pulls on upstream
      c.expectNoMessage(50.millis) // message should not be emitted yet
      p.sendComplete() // Force Grouped to emit the small group
      c.expectNext(50.millis, immutable.Seq(1))
      c.expectComplete()
    }

    "fail during stream initialization when minWeight is negative" in {
      val ex = the[IllegalArgumentException] thrownBy Source(1 to 5)
        .groupedWeighted(-1)(_ => 1L)
        .to(Sink.collection)
        .run()
      ex.getMessage should be("requirement failed: minWeight must be greater than 0")
    }

    "fail during stream initialization when minWeight is 0" in {
      val ex = the[IllegalArgumentException] thrownBy Source(1 to 5)
        .groupedWeighted(0)(_ => 1L)
        .to(Sink.collection)
        .run()
      ex.getMessage should be("requirement failed: minWeight must be greater than 0")
    }

    "fail the stage when costFn has a negative result" in {
      val p = TestPublisher.probe[Int]()
      val c = TestSubscriber.probe[immutable.Seq[Int]]()
      Source.fromPublisher(p).groupedWeighted(1)(_ => -1L).to(Sink.fromSubscriber(c)).run()
      c.expectSubscription().request(1) // create downstream demand so Grouped pulls on upstream
      c.expectNoMessage(50.millis) // shouldn't fail until the message is sent
      p.sendNext(1) // Send a message that will result in negative cost
      val error = c.expectError()
      error shouldBe an[IllegalArgumentException]
      error.getMessage should be("Negative weight [-1] for element [1] is not allowed")
    }

    "fail the stream when costFn throws and supervision is Stop" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 5)
        .groupedWeighted(10)(i => if (i == 3) throw ex else i.toLong)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(Sink.ignore)

      result.failed.futureValue shouldBe ex
    }

    "fail the stream when costFn throws and no supervision attribute is set (default Stop)" in {
      val ex = new RuntimeException("boom")
      val result = Source(1 to 5)
        .groupedWeighted(10)(i => if (i == 3) throw ex else i.toLong)
        .runWith(Sink.ignore)

      result.failed.futureValue shouldBe ex
    }

    "resume and keep the partially accumulated group when costFn throws (Resume)" in {
      // minWeight=10 so [1,2] stay buffered when element 3 throws; Resume must keep them.
      val result = Source(1 to 5)
        .groupedWeighted(10)(i => if (i == 3) throw new RuntimeException("boom") else i.toLong)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(Sink.seq)

      result.futureValue shouldBe Seq(Seq(1, 2, 4, 5)) // 3 skipped, [1,2] preserved, then 4,5 fill the group
    }

    "resume and flush the buffered group at completion when costFn throws on the last element (Resume)" in {
      // minWeight=100 is never reached, so the group is flushed only at upstream finish.
      val result = Source(1 to 5)
        .groupedWeighted(100)(i => if (i == 5) throw new RuntimeException("boom") else i.toLong)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(Sink.seq)

      result.futureValue shouldBe Seq(Seq(1, 2, 3, 4)) // 5 skipped, [1,2,3,4] flushed at completion
    }

    "restart and drop the current group when costFn throws and supervision is Restart" in {
      val result = Source(1 to 5)
        .groupedWeighted(6)(i => if (i == 3) throw new RuntimeException("boom") else i.toLong)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(Sink.seq)

      result.futureValue shouldBe Seq(Seq(4, 5))
    }
  }
}

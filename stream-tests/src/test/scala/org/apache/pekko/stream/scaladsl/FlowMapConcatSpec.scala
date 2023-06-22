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

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.ActorAttributes
import pekko.stream.Supervision
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink

class FlowMapConcatSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A MapConcat" must {

    "map and concat" in {
      val script = Script(
        Seq(0) -> Seq(),
        Seq(1) -> Seq(1),
        Seq(2) -> Seq(2, 2),
        Seq(3) -> Seq(3, 3, 3),
        Seq(2) -> Seq(2, 2),
        Seq(1) -> Seq(1))
      TestConfig.RandomTestRange.foreach(_ => runScript(script)(_.mapConcat(x => (1 to x).map(_ => x))))
    }

    "map and concat iterator" in {
      val script = Script(
        Seq(0) -> Seq(),
        Seq(1) -> Seq(1),
        Seq(2) -> Seq(2, 2),
        Seq(3) -> Seq(3, 3, 3),
        Seq(2) -> Seq(2, 2),
        Seq(1) -> Seq(1))
      TestConfig.RandomTestRange.foreach(_ => runScript(script)(_.mapConcat(x => Iterator.fill(x)(x))))
    }

    "map and concat grouping with slow downstream" in {
      val s = TestSubscriber.manualProbe[Int]()
      val input = (1 to 20).grouped(5).toList
      Source(input).mapConcat(identity).map(x => { Thread.sleep(10); x }).runWith(Sink.fromSubscriber(s))
      val sub = s.expectSubscription()
      sub.request(100)
      for (i <- 1 to 20) s.expectNext(i)
      s.expectComplete()
    }

    "be able to resume" in {
      val ex = new Exception("TEST") with NoStackTrace

      Source(1 to 5)
        .mapConcat(x => if (x == 3) throw ex else List(x))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(TestSink.probe[Int])
        .request(4)
        .expectNext(1, 2, 4, 5)
        .expectComplete()
    }

    "be able to resume (iterator)" in {
      val ex = new Exception("TEST") with NoStackTrace

      Source(1 to 5)
        .mapConcat(x => if (x == 3) throw ex else scala.collection.Iterator(x))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(TestSink.probe[Int])
        .request(4)
        .expectNext(1, 2, 4, 5)
        .expectComplete()
    }

  }

}

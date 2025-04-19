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

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.ActorAttributes._
import pekko.stream.Supervision._
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink

class FlowTakeWhileSpec extends StreamSpec {

  "A TakeWhile" must {

    "take while predicate is true" in {
      Source(1 to 4).takeWhile(_ < 3).runWith(TestSink.probe[Int]).request(3).expectNext(1, 2).expectComplete()
    }

    "complete the future for an empty stream" in {
      Source.empty[Int].takeWhile(_ < 2).runWith(TestSink.probe[Int]).request(1).expectComplete()
    }

    "can be used to implemented takeUntil" in {
      Source(List("message", "message", "DONE", "unexpected"))
        .takeUntil(_ == "DONE")
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue should be(Seq("message", "message", "DONE"))
    }

    "continue if error" in {
      val testException = new Exception("test") with NoStackTrace

      Source(1 to 4)
        .takeWhile(a => if (a == 3) throw testException else true)
        .withAttributes(supervisionStrategy(resumingDecider))
        .runWith(TestSink.probe[Int])
        .request(4)
        .expectNext(1, 2, 4)
        .expectComplete()
    }

    "emit the element that caused the predicate to return false and then no more with inclusive set" in {
      Source(1 to 10)
        .takeWhile(_ < 3, true)
        .runWith(TestSink.probe[Int])
        .request(4)
        .expectNext(1, 2, 3)
        .expectComplete()
    }

  }

}

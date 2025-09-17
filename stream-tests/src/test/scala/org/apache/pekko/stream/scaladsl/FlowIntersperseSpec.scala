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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }

class FlowIntersperseSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "A Intersperse" must {
    "inject element between existing elements" in {
      val probe = Source(List(1, 2, 3)).map(_.toString).intersperse(",").runWith(TestSink.probe)

      probe.expectSubscription()
      probe.toStrict(1.second).mkString("") should ===(List(1, 2, 3).mkString(","))
    }

    "inject element between existing elements, when downstream is fold" in {
      val concated = Source(List(1, 2, 3)).map(_.toString).intersperse(",").runFold("")(_ + _)

      concated.futureValue should ===("1,2,3")
    }

    "inject element between existing elements, and surround with []" in {
      val probe = Source(List(1, 2, 3)).map(_.toString).intersperse("[", ",", "]").runWith(TestSink.probe)

      probe.toStrict(1.second).mkString("") should ===(List(1, 2, 3).mkString("[", ",", "]"))
    }

    "demonstrate how to prepend only" in {
      val probe = (Source.single(">> ") ++ Source(List("1", "2", "3")).intersperse(",")).runWith(TestSink.probe)

      probe.toStrict(1.second).mkString("") should ===(List(1, 2, 3).mkString(">> ", ",", ""))
    }

    "surround empty stream with []" in {
      val probe = Source(List()).intersperse("[", ",", "]").runWith(TestSink.probe)

      probe.expectSubscription()
      probe.toStrict(1.second).mkString("") should ===(List().mkString("[", ",", "]"))
    }

    "surround single element stream with []" in {
      val probe = Source(List(1)).map(_.toString).intersperse("[", ",", "]").runWith(TestSink.probe)

      probe.expectSubscription()
      probe.toStrict(1.second).mkString("") should ===(List(1).mkString("[", ",", "]"))
    }

    "complete the stage when the Source has been completed" in {
      val (p1, p2) = TestSource.probe[String].intersperse(",").toMat(TestSink.probe[String])(Keep.both).run()
      p2.request(10)
      p1.sendNext("a").sendNext("b").sendComplete()
      p2.expectNext("a").expectNext(",").expectNext("b").expectComplete()
    }

    "complete the stage when the Sink has been cancelled" in {
      val (p1, p2) = TestSource.probe[String].intersperse(",").toMat(TestSink.probe[String])(Keep.both).run()
      p2.request(10)
      p1.sendNext("a").sendNext("b")
      p2.expectNext("a").expectNext(",").cancel()
      p1.expectCancellation()
    }
  }

}

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

import java.util.concurrent.ThreadLocalRandom.{ current => random }

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.{ ActorAttributes, Supervision }
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils._
import pekko.stream.testkit.scaladsl.TestSink

class FlowScanSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "A Scan" must {

    def scan(s: Source[Int, NotUsed], duration: Duration = 5.seconds): immutable.Seq[Int] =
      Await.result(s.scan(0)(_ + _).runFold(immutable.Seq.empty[Int])(_ :+ _), duration)

    "Scan" in {
      val v = Vector.fill(random.nextInt(100, 1000))(random.nextInt())
      scan(Source(v)) should be(v.scan(0)(_ + _))
    }

    "Scan empty failed" in {
      val e = new Exception("fail!")
      (intercept[Exception](scan(Source.failed[Int](e))) should be).theSameInstanceAs(e)
    }

    "Scan empty" in {
      scan(Source.empty[Int]) should be(Vector.empty[Int].scan(0)(_ + _))
    }

    "emit values promptly" in {
      val f = Source.single(1).concat(Source.maybe[Int]).scan(0)(_ + _).take(2).runWith(Sink.seq)
      Await.result(f, 1.second) should ===(Seq(0, 1))
    }

    "restart properly" in {
      import ActorAttributes._
      val scan = Flow[Int]
        .scan(0) { (old, current) =>
          require(current > 0)
          old + current
        }
        .withAttributes(supervisionStrategy(Supervision.restartingDecider))
      Source(List(1, 3, -1, 5, 7)).via(scan).runWith(TestSink.probe).toStrict(1.second) should ===(
        Seq(0, 1, 4, 0, 5, 12))
    }

    "resume properly" in {
      import ActorAttributes._
      val scan = Flow[Int]
        .scan(0) { (old, current) =>
          require(current > 0)
          old + current
        }
        .withAttributes(supervisionStrategy(Supervision.resumingDecider))
      Source(List(1, 3, -1, 5, 7)).via(scan).runWith(TestSink.probe).toStrict(1.second) should ===(Seq(0, 1, 4, 9, 16))
    }

    "scan normally for empty source" in {
      Source
        .empty[Int]
        .scan(0) { case (a, b) => a + b }
        .runWith(TestSink.probe[Int])
        .request(2)
        .expectNext(0)
        .expectComplete()
    }

    "fail when upstream failed" in {
      val ex = TE("")
      Source
        .failed[Int](ex)
        .scan(0) { case (a, b) => a + b }
        .runWith(TestSink.probe[Int])
        .request(2)
        .expectNextOrError(0, ex)
    }
  }
}

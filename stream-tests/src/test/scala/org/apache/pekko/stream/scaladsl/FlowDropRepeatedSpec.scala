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
import pekko.stream.ActorAttributes._
import pekko.stream.Supervision._
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink

class FlowDropRepeatedSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {
  private val TE = new Exception("TEST") with NoStackTrace {
    override def toString = "TE"
  }

  "A DropRepeated" must {

    "remove duplicated elements" in {
      Source(List(1, 2, 2, 3, 3, 1, 4))
        .dropRepeated()
        .runWith(TestSink[Int]())
        .request(7)
        .expectNext(1, 2, 3, 1, 4)
        .expectComplete()
    }

    "only distinct with the previous element" in {
      Source(List(1, 2, 2, 3, 2, 3))
        .dropRepeated(_ == _)
        .runWith(TestSink[Int]())
        .request(6)
        .expectNext(1, 2, 3, 2, 3)
        .expectComplete()
    }

    "continue if error" in {
      Source(List(1, 2, 2, 3, 3, 1, 4))
        .dropRepeated((a, b) => {
          if (b == 2) throw TE
          else a == b
        })
        .withAttributes(supervisionStrategy(resumingDecider))
        .runWith(TestSink[Int]())
        .request(7)
        .expectNext(1, 3, 1, 4)
        .expectComplete()
    }

    "restart if error" in {
      Source(List(1, 2, 2, 3, 3, 1, 4))
        .dropRepeated((a, b) => {
          if (b == 2) throw TE
          else a == b
        })
        .withAttributes(supervisionStrategy(restartingDecider))
        .runWith(TestSink[Int]())
        .request(6)
        .expectNext(1, 2, 3, 1, 4)
        .expectComplete()
    }

  }

}

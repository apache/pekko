/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.ThreadLocalRandom.{ current => random }

import org.apache.pekko
import pekko.stream.ActorAttributes._
import pekko.stream.Supervision._
import pekko.stream.testkit.ScriptedTest
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils.TE
import pekko.stream.testkit.scaladsl.TestSink

class FlowCollectSpec extends StreamSpec with ScriptedTest {

  "A Collect" must {

    "collect" in {
      def script =
        Script(TestConfig.RandomTestRange.map { _ =>
          val x = random.nextInt(0, 10000)
          Seq(x) -> (if ((x & 1) == 0) Seq((x * x).toString) else Seq.empty[String])
        }: _*)
      TestConfig.RandomTestRange.foreach(_ =>
        runScript(script)(_.collect {
          case x if x % 2 == 0 => (x * x).toString
        }))
    }

    "restart when Collect throws" in {
      val pf: PartialFunction[Int, Int] = { case x: Int => if (x == 2) throw TE("") else x }
      Source(1 to 3)
        .collect(pf)
        .withAttributes(supervisionStrategy(restartingDecider))
        .runWith(TestSink.probe[Int])
        .request(1)
        .expectNext(1)
        .request(1)
        .expectNext(3)
        .request(1)
        .expectComplete()
    }

  }

}

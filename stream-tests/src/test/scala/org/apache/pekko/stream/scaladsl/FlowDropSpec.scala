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

import org.apache.pekko.stream.testkit._

class FlowDropSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A Drop" must {

    "drop" in {
      def script(d: Int) =
        Script(TestConfig.RandomTestRange.map { n =>
          Seq(n) -> (if (n <= d) Nil else Seq(n))
        }: _*)
      TestConfig.RandomTestRange.foreach { _ =>
        val d = Math.min(Math.max(random.nextInt(-10, 60), 0), 50)
        runScript(script(d))(_.drop(d))
      }
    }

    "not drop anything for negative n" in {
      val probe = TestSubscriber.manualProbe[Int]()
      Source(List(1, 2, 3)).drop(-1).to(Sink.fromSubscriber(probe)).run()
      probe.expectSubscription().request(10)
      probe.expectNext(1)
      probe.expectNext(2)
      probe.expectNext(3)
      probe.expectComplete()
    }

  }

}

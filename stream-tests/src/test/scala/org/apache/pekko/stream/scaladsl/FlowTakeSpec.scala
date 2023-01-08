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

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.impl.ActorSubscriberMessage.OnComplete
import pekko.stream.impl.ActorSubscriberMessage.OnNext
import pekko.stream.impl.RequestMore
import pekko.stream.testkit._

class FlowTakeSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  muteDeadLetters(classOf[OnNext], OnComplete.getClass, classOf[RequestMore[_]])()

  "A Take" must {

    "take" in {
      def script(d: Int) =
        Script(TestConfig.RandomTestRange.map { n =>
          Seq(n) -> (if (n > d) Nil else Seq(n))
        }: _*)
      TestConfig.RandomTestRange.foreach { _ =>
        val d = Math.min(Math.max(random.nextInt(-10, 60), 0), 50)
        runScript(script(d))(_.take(d))
      }
    }

    "not take anything for negative n" in {
      val probe = TestSubscriber.manualProbe[Int]()
      Source(List(1, 2, 3)).take(-1).to(Sink.fromSubscriber(probe)).run()
      probe.expectSubscription().request(10)
      probe.expectComplete()
    }

    "complete eagerly when zero or less is taken independently of upstream completion" in {
      Await.result(Source.maybe[Int].take(0).runWith(Sink.ignore), 3.second)
      Await.result(Source.maybe[Int].take(-1).runWith(Sink.ignore), 3.second)
    }

  }

}

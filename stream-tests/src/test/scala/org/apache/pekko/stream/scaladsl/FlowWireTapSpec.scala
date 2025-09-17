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
import pekko.Done
import pekko.stream.testkit.Utils._
import pekko.stream.testkit._

class FlowWireTapSpec extends StreamSpec("pekko.stream.materializer.debug.fuzzing-mode = off") {

  import system.dispatcher

  "A wireTap" must {

    "call the procedure for each element" in {
      Source(1 to 100).wireTap(testActor ! _).runWith(Sink.ignore).futureValue
      (1 to 100).foreach { i =>
        expectMsg(i)
      }
    }

    "complete the future for an empty stream" in {
      Source.empty[String].wireTap(testActor ! _).runWith(Sink.ignore).foreach { _ =>
        testActor ! "done"
      }
      expectMsg("done")
    }

    "yield the first error" in {
      val p = TestPublisher.manualProbe[Int]()
      Source.fromPublisher(p).wireTap(testActor ! _).runWith(Sink.ignore).failed.foreach { ex =>
        testActor ! ex
      }
      val proc = p.expectSubscription()
      proc.expectRequest()
      val rte = new RuntimeException("ex") with NoStackTrace
      proc.sendError(rte)
      expectMsg(rte)
    }

    "not cause subsequent stages to be failed if throws (same as wireTap(Sink))" in {
      val error = TE("Boom!")
      val future = Source.single(1).wireTap(_ => throw error).runWith(Sink.ignore)
      future.futureValue shouldEqual Done
    }
  }

}

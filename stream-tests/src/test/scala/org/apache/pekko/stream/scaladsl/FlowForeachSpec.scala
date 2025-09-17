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

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.testkit.Utils._
import pekko.stream.testkit._

class FlowForeachSpec extends StreamSpec {

  import system.dispatcher

  "A runForeach" must {

    "call the procedure for each element" in {
      Source(1 to 3).runForeach(testActor ! _).foreach { _ =>
        testActor ! "done"
      }
      expectMsg(1)
      expectMsg(2)
      expectMsg(3)
      expectMsg("done")
    }

    "complete the future for an empty stream" in {
      Source.empty[String].runForeach(testActor ! _).foreach { _ =>
        testActor ! "done"
      }
      expectMsg("done")
    }

    "yield the first error" in {
      val p = TestPublisher.manualProbe[Int]()
      Source.fromPublisher(p).runForeach(testActor ! _).failed.foreach { ex =>
        testActor ! ex
      }
      val proc = p.expectSubscription()
      proc.expectRequest()
      val rte = new RuntimeException("ex") with NoStackTrace
      proc.sendError(rte)
      expectMsg(rte)
    }

    "complete future with failure when function throws" in {
      val error = TE("Boom!")
      val future = Source.single(1).runForeach(_ => throw error)
      the[Exception] thrownBy Await.result(future, 3.seconds) should be(error)
    }

  }

}

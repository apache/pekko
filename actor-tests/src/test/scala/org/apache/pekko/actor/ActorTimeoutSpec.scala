/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.pattern.{ ask, AskTimeoutException }
import pekko.testkit._
import pekko.testkit.TestEvent._
import pekko.util.Timeout

class ActorTimeoutSpec extends PekkoSpec {

  val testTimeout = 200.millis.dilated
  val leeway = 500.millis.dilated

  system.eventStream.publish(Mute(EventFilter.warning(pattern = ".*unhandled message from.*hallo")))

  "An Actor-based Future" must {

    "use implicitly supplied timeout" in {
      implicit val timeout = Timeout(testTimeout)
      val echo = system.actorOf(Props.empty)
      val f = echo ? "hallo"
      intercept[AskTimeoutException] { Await.result(f, testTimeout + leeway) }
    }

    "use explicitly supplied timeout" in {
      val echo = system.actorOf(Props.empty)
      val f = echo.?("hallo")(testTimeout)
      intercept[AskTimeoutException] { Await.result(f, testTimeout + leeway) }
    }
  }
}

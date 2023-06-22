/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch

import java.lang.management.ManagementFactory

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.TestKit

class DispatcherShutdownSpec extends AnyWordSpec with Matchers {

  "pekko dispatcher" should {

    "eventually shutdown when used after system terminate" in {

      val threads = ManagementFactory.getThreadMXBean()
      def threadCount =
        threads
          .dumpAllThreads(false, false)
          .toList
          .map(_.getThreadName)
          .filter(name =>
            name.startsWith("DispatcherShutdownSpec-pekko.actor.default") || name.startsWith(
              "DispatcherShutdownSpec-pekko.actor.internal")) // nothing is run on default without any user actors started
          .size

      val system = ActorSystem("DispatcherShutdownSpec")
      threadCount should be > 0

      Await.ready(system.terminate(), 1.second)
      Await.ready(Future(pekko.Done)(system.dispatcher), 1.second)

      TestKit.awaitCond(threadCount == 0, 3.second)
    }

  }

}

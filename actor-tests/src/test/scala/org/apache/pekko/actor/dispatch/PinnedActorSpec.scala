/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.dispatch

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import scala.concurrent.Await

import org.scalatest.BeforeAndAfterEach

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.pattern.ask
import pekko.testkit._
import pekko.testkit.PekkoSpec

object PinnedActorSpec {
  val config = """
    pinned-dispatcher {
      executor = thread-pool-executor
      type = PinnedDispatcher
    }
    """

  class TestActor extends Actor {
    def receive = {
      case "Hello"   => sender() ! "World"
      case "Failure" => throw new RuntimeException("Expected exception; to test fault-tolerance")
    }
  }
}

class PinnedActorSpec extends PekkoSpec(PinnedActorSpec.config) with BeforeAndAfterEach with DefaultTimeout {
  import PinnedActorSpec._

  "A PinnedActor" must {

    "support tell" in {
      val oneWay = new CountDownLatch(1)
      val actor = system.actorOf(
        Props(new Actor { def receive = { case "OneWay" => oneWay.countDown() } }).withDispatcher("pinned-dispatcher"))
      actor ! "OneWay"
      assert(oneWay.await(1, TimeUnit.SECONDS))
      system.stop(actor)
    }

    "support ask/reply" in {
      val actor = system.actorOf(Props[TestActor]().withDispatcher("pinned-dispatcher"))
      assert("World" === Await.result(actor ? "Hello", timeout.duration))
      system.stop(actor)
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.routing

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import pekko.actor.typed.{ ActorRef, Behavior, DispatcherSelector }
import pekko.actor.typed.scaladsl.{ Behaviors, Routers }

object PoolRouterSpec {

  object RouteeBehavior {

    final case class WhichDispatcher(replyTo: ActorRef[String])

    def apply(): Behavior[WhichDispatcher] = Behaviors.receiveMessage {
      case WhichDispatcher(replyTo) =>
        replyTo ! Thread.currentThread.getName
        Behaviors.same
    }
  }
}

class PoolRouterSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {
  import PoolRouterSpec.RouteeBehavior
  import RouteeBehavior.WhichDispatcher

  "PoolRouter" must {

    "use the default dispatcher per default for its routees" in {
      val probe = createTestProbe[String]()
      val pool = spawn(Routers.pool(1)(RouteeBehavior()), "default-pool")
      pool ! WhichDispatcher(probe.ref)

      val response = probe.receiveMessage()
      response should startWith("PoolRouterSpec-pekko.actor.default-dispatcher")
    }

    "use the specified dispatcher for its routees" in {
      val probe = createTestProbe[String]()
      val pool = spawn(
        Routers.pool(1)(RouteeBehavior()).withRouteeProps(DispatcherSelector.blocking()),
        "pool-with-blocking-routees")
      pool ! WhichDispatcher(probe.ref)

      val response = probe.receiveMessage()
      response should startWith("PoolRouterSpec-pekko.actor.default-blocking-io-dispatcher")
    }
  }
}

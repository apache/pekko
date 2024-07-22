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

package org.apache.pekko.cluster.typed

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, LoggingTestKit, ScalaTestWithActorTestKit, TestProbe }
import pekko.actor.typed.internal.ActorMdc
import pekko.actor.typed.scaladsl.Behaviors

object ClusterActorLoggingSpec {
  def config = ConfigFactory.parseString("""
    pekko.actor.provider = cluster
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.remote.artery.canonical.hostname = 127.0.0.1
    # generous timeout for cluster forming probes
    pekko.actor.testkit.typed.default-timeout = 10s
    pekko.actor.testkit.typed.filter-leeway = 10s
    """)
}

class ClusterActorLoggingSpec
    extends ScalaTestWithActorTestKit(ClusterActorLoggingSpec.config)
    with AnyWordSpecLike
    with Matchers
    with LogCapturing {

  "Logging from an actor in a cluster" must {

    "include host and port in sourceActorSystem mdc entry" in {

      def addressString = system.classicSystem.asInstanceOf[ExtendedActorSystem].provider.addressString

      val probe = TestProbe[String]("pong")

      val behavior =
        Behaviors.setup[String] { context =>
          Behaviors.receiveMessage {
            case "ping" =>
              context.log.info("Starting")
              probe.ref ! "pong"
              Behaviors.same
          }
        }

      LoggingTestKit
        .info("Starting")
        .withCustom { event =>
          event.mdc.contains(ActorMdc.PekkoAddressKey) && event.mdc(ActorMdc.PekkoAddressKey) == addressString
        }
        .withLoggerName("org.apache.pekko.cluster.typed.ClusterActorLoggingSpec")
        .expect {
          val actorRef = spawn(behavior)
          actorRef ! "ping"
          probe.expectMessage("pong")
        }
    }
  }
}

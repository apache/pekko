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

package org.apache.pekko.cluster

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.Address
import pekko.actor.Props
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender

object StartupWithOneThreadSpec {
  val config = """
    pekko.actor.provider = "cluster"
    pekko.actor.creation-timeout = 10s
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0

    pekko.actor.default-dispatcher {
      executor = thread-pool-executor
      thread-pool-executor {
        fixed-pool-size = 1
      }
    }
    pekko.actor.internal-dispatcher = pekko.actor.default-dispatcher 
    """

  final case class GossipTo(address: Address)

  def testProps =
    Props(new Actor with ActorLogging {
      val cluster = Cluster(context.system)
      log.debug(s"started ${cluster.selfAddress} ${Thread.currentThread().getName}")
      def receive = {
        case msg => sender() ! msg
      }
    })
}

class StartupWithOneThreadSpec(startTime: Long) extends PekkoSpec(StartupWithOneThreadSpec.config) with ImplicitSender {
  import StartupWithOneThreadSpec._

  def this() = this(System.nanoTime())

  "A Cluster" must {

    "startup with one dispatcher thread" in {
      // This test failed before fixing #17253 when adding a sleep before the
      // Await of GetClusterCoreRef in the Cluster extension constructor.
      // The reason was that other cluster actors were started too early and
      // they also tried to get the Cluster extension and thereby blocking
      // dispatcher threads.
      // Note that the Cluster extension is started via ClusterActorRefProvider
      // before ActorSystem.apply returns, i.e. in the constructor of PekkoSpec.
      (System.nanoTime - startTime).nanos.toMillis should be <
      (system.settings.CreationTimeout.duration - 2.second).toMillis
      system.actorOf(testProps) ! "hello"
      system.actorOf(testProps) ! "hello"
      system.actorOf(testProps) ! "hello"

      Cluster(system)
      (System.nanoTime - startTime).nanos.toMillis should be <
      (system.settings.CreationTimeout.duration - 2.second).toMillis

      expectMsg("hello")
      expectMsg("hello")
      expectMsg("hello")
    }

  }
}

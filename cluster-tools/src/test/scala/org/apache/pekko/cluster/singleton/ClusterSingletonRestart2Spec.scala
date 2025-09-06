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

package org.apache.pekko.cluster.singleton

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorSystem
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.cluster.UniqueAddress
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe

object ClusterSingletonRestart2Spec {
  def singletonActorProps: Props = Props(new Singleton)

  class Singleton extends Actor {
    def receive = {
      case _ => sender() ! Cluster(context.system).selfUniqueAddress
    }
  }
}

class ClusterSingletonRestart2Spec
    extends PekkoSpec("""
  pekko.loglevel = INFO
  pekko.cluster.roles = [singleton]
  pekko.actor.provider = org.apache.pekko.cluster.ClusterActorRefProvider
  pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
  pekko.cluster.testkit.auto-down-unreachable-after = 2s
  pekko.cluster.singleton.min-number-of-hand-over-retries = 5
  pekko.remote {
    classic.netty.tcp {
      hostname = "127.0.0.1"
      port = 0
    }
    artery.canonical {
      hostname = "127.0.0.1"
      port = 0
    }
  }
  pekko.actor.serialization-bindings {
    # there is no serializer for UniqueAddress, not intended to be sent as a standalone message
    "org.apache.pekko.cluster.UniqueAddress" = jackson-cbor
  }
  """) {

  val sys1 = ActorSystem(system.name, system.settings.config)
  val sys2 = ActorSystem(system.name, system.settings.config)
  val sys3 = ActorSystem(
    system.name,
    ConfigFactory.parseString("pekko.cluster.roles = [other]").withFallback(system.settings.config))
  var sys4: ActorSystem = null

  def join(from: ActorSystem, to: ActorSystem): Unit = {
    if (Cluster(from).selfRoles.contains("singleton"))
      from.actorOf(
        ClusterSingletonManager.props(
          singletonProps = ClusterSingletonRestart2Spec.singletonActorProps,
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(from).withRole("singleton")),
        name = "echo")

    within(45.seconds) {
      awaitAssert {
        Cluster(from).join(Cluster(to).selfAddress)
        Cluster(from).state.members.map(_.uniqueAddress) should contain(Cluster(from).selfUniqueAddress)
        Cluster(from).state.members.unsorted.map(_.status) should ===(Set(MemberStatus.Up))
      }
    }
  }

  "Restarting cluster node during hand over" must {
    "start singletons in restarted node" in {
      join(sys1, sys1)
      join(sys2, sys1)
      join(sys3, sys1)

      val proxy3 = sys3.actorOf(
        ClusterSingletonProxy.props("user/echo", ClusterSingletonProxySettings(sys3).withRole("singleton")),
        "proxy3")

      within(5.seconds) {
        awaitAssert {
          val probe = TestProbe()(sys3)
          proxy3.tell("hello", probe.ref)
          probe.expectMsgType[UniqueAddress](1.second) should be(Cluster(sys1).selfUniqueAddress)
        }
      }

      Cluster(sys1).leave(Cluster(sys1).selfAddress)

      // at the same time, shutdown sys2, which would be the expected next singleton node
      shutdown(sys2)
      // it will be downed by the join attempts of the new incarnation

      // then restart it
      sys4 = {
        val sys2port = Cluster(sys2).selfAddress.port.get

        val sys4Config =
          ConfigFactory.parseString(s"""
            pekko.remote.artery.canonical.port=$sys2port
            pekko.remote.classic.netty.tcp.port=$sys2port
            """).withFallback(system.settings.config)

        ActorSystem(system.name, sys4Config)
      }
      join(sys4, sys3)

      // let it stabilize
      Thread.sleep(5000)

      within(10.seconds) {
        awaitAssert {
          val probe = TestProbe()(sys3)
          proxy3.tell("hello2", probe.ref)
          // note that sys3 doesn't have the required singleton role, so singleton instance should be
          // on the restarted node
          probe.expectMsgType[UniqueAddress](1.second) should be(Cluster(sys4).selfUniqueAddress)
        }
      }

    }
  }

  override def afterTermination(): Unit = {
    shutdown(sys1)
    shutdown(sys2)
    shutdown(sys3)
    if (sys4 != null)
      shutdown(sys4)
  }
}

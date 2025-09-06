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
import pekko.actor.ActorSystem
import pekko.actor.PoisonPill
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

class ClusterSingletonRestartSpec
    extends PekkoSpec("""
  pekko.loglevel = INFO
  pekko.actor.provider = org.apache.pekko.cluster.ClusterActorRefProvider
  pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
  pekko.cluster.testkit.auto-down-unreachable-after = 2s
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
  """) {

  val sys1 = ActorSystem(system.name, system.settings.config)
  val sys2 = ActorSystem(system.name, system.settings.config)
  var sys3: ActorSystem = null

  def join(from: ActorSystem, to: ActorSystem): Unit = {
    from.actorOf(
      ClusterSingletonManager.props(
        singletonProps = TestActors.echoActorProps,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(from)),
      name = "echo")

    within(10.seconds) {
      awaitAssert {
        Cluster(from).join(Cluster(to).selfAddress)
        Cluster(from).state.members.map(_.uniqueAddress) should contain(Cluster(from).selfUniqueAddress)
        Cluster(from).state.members.unsorted.map(_.status) should ===(Set(MemberStatus.Up))
      }
    }
  }

  "Restarting cluster node with same hostname and port" must {
    "hand-over to next oldest" in {
      join(sys1, sys1)
      join(sys2, sys1)

      val proxy2 = sys2.actorOf(ClusterSingletonProxy.props("user/echo", ClusterSingletonProxySettings(sys2)), "proxy2")

      within(5.seconds) {
        awaitAssert {
          val probe = TestProbe()(sys2)
          proxy2.tell("hello", probe.ref)
          probe.expectMsg(1.second, "hello")
        }
      }

      shutdown(sys1)
      // it will be downed by the join attempts of the new incarnation

      sys3 = {
        val sys1port = Cluster(sys1).selfAddress.port.get

        val sys3Config =
          ConfigFactory.parseString(s"""
            pekko.remote.artery.canonical.port=$sys1port
            pekko.remote.classic.netty.tcp.port=$sys1port
            """).withFallback(system.settings.config)

        ActorSystem(system.name, sys3Config)
      }
      join(sys3, sys2)

      within(5.seconds) {
        awaitAssert {
          val probe = TestProbe()(sys2)
          proxy2.tell("hello2", probe.ref)
          probe.expectMsg(1.second, "hello2")
        }
      }

      Cluster(sys2).leave(Cluster(sys2).selfAddress)

      within(15.seconds) {
        awaitAssert {
          Cluster(sys3).state.members.map(_.uniqueAddress) should ===(Set(Cluster(sys3).selfUniqueAddress))
        }
      }

      val proxy3 = sys3.actorOf(ClusterSingletonProxy.props("user/echo", ClusterSingletonProxySettings(sys3)), "proxy3")

      within(5.seconds) {
        awaitAssert {
          val probe = TestProbe()(sys3)
          proxy3.tell("hello3", probe.ref)
          probe.expectMsg(1.second, "hello3")
        }
      }

    }
  }

  override def afterTermination(): Unit = {
    shutdown(sys1)
    shutdown(sys2)
    if (sys3 != null)
      shutdown(sys3)
  }
}

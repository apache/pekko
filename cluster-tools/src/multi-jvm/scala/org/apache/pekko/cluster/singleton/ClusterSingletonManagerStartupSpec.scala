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

package org.apache.pekko.cluster.singleton

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.testkit.STMultiNodeSpec
import pekko.testkit._

object ClusterSingletonManagerStartupSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
    pekko.cluster.testkit.auto-down-unreachable-after = 0s
    """))

  case object EchoStarted

  /**
   * The singleton actor
   */
  class Echo extends Actor {
    def receive = {
      case _ =>
        sender() ! self
    }
  }
}

class ClusterSingletonManagerStartupMultiJvmNode1 extends ClusterSingletonManagerStartupSpec
class ClusterSingletonManagerStartupMultiJvmNode2 extends ClusterSingletonManagerStartupSpec
class ClusterSingletonManagerStartupMultiJvmNode3 extends ClusterSingletonManagerStartupSpec

class ClusterSingletonManagerStartupSpec
    extends MultiNodeSpec(ClusterSingletonManagerStartupSpec)
    with STMultiNodeSpec
    with ImplicitSender {
  import ClusterSingletonManagerStartupSpec._

  override def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system).join(node(to).address)
      createSingleton()
    }
  }

  def createSingleton(): ActorRef = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(classOf[Echo]),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
      name = "echo")
  }

  lazy val echoProxy: ActorRef = {
    system.actorOf(
      ClusterSingletonProxy
        .props(singletonManagerPath = "/user/echo", settings = ClusterSingletonProxySettings(system)),
      name = "echoProxy")
  }

  "Startup of Cluster Singleton" must {

    "be quick" in {
      join(first, first)
      join(second, first)
      join(third, first)

      within(7.seconds) {
        awaitAssert {
          val members = Cluster(system).state.members
          members.size should be(3)
          members.forall(_.status == MemberStatus.Up) should be(true)
        }
      }
      enterBarrier("all-up")

      // the singleton instance is expected to start "instantly"
      echoProxy ! "hello"
      expectMsgType[ActorRef](3.seconds)

      enterBarrier("done")
    }

  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.singleton

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, Address, PoisonPill, Props }
import pekko.cluster.Cluster
import pekko.cluster.ClusterSettings
import pekko.remote.testkit.{ MultiNodeConfig, MultiNodeSpec, STMultiNodeSpec }
import pekko.serialization.jackson.CborSerializable
import pekko.testkit.ImplicitSender

import com.typesafe.config.ConfigFactory

object MultiDcSingletonManagerSpec extends MultiNodeConfig {
  val controller = role("controller")
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off"""))

  nodeConfig(controller) {
    ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = one
      pekko.cluster.roles = []""")
  }

  nodeConfig(first) {
    ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = one
      pekko.cluster.roles = [ worker ]""")
  }
  nodeConfig(second, third) {
    ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = two
      pekko.cluster.roles = [ worker ]""")
  }
}

class MultiDcSingletonManagerMultiJvmNode1 extends MultiDcSingletonManagerSpec
class MultiDcSingletonManagerMultiJvmNode2 extends MultiDcSingletonManagerSpec
class MultiDcSingletonManagerMultiJvmNode3 extends MultiDcSingletonManagerSpec
class MultiDcSingletonManagerMultiJvmNode4 extends MultiDcSingletonManagerSpec

class MultiDcSingleton extends Actor with ActorLogging {
  import MultiDcSingleton._

  val cluster = Cluster(context.system)

  override def receive: Receive = {
    case Ping =>
      sender() ! Pong(cluster.settings.SelfDataCenter, cluster.selfAddress, cluster.selfRoles)
  }
}
object MultiDcSingleton {
  case object Ping extends CborSerializable
  case class Pong(fromDc: String, fromAddress: Address, roles: Set[String]) extends CborSerializable
}

abstract class MultiDcSingletonManagerSpec
    extends MultiNodeSpec(MultiDcSingletonManagerSpec)
    with STMultiNodeSpec
    with ImplicitSender {
  import MultiDcSingletonManagerSpec._

  override def initialParticipants = roles.size

  val cluster = Cluster(system)
  cluster.join(node(controller).address)
  enterBarrier("nodes-joined")

  val worker = "worker"

  "A SingletonManager in a multi data center cluster" must {
    "start a singleton instance for each data center" in {

      runOn(first, second, third) {
        system.actorOf(
          ClusterSingletonManager
            .props(Props[MultiDcSingleton](), PoisonPill, ClusterSingletonManagerSettings(system).withRole(worker)),
          "singletonManager")
      }

      val proxy = system.actorOf(
        ClusterSingletonProxy.props("/user/singletonManager", ClusterSingletonProxySettings(system).withRole(worker)))

      enterBarrier("managers-started")

      proxy ! MultiDcSingleton.Ping
      val pong = expectMsgType[MultiDcSingleton.Pong](20.seconds)

      enterBarrier("pongs-received")

      pong.fromDc should equal(Cluster(system).settings.SelfDataCenter)
      pong.roles should contain(worker)
      runOn(controller, first) {
        pong.roles should contain(ClusterSettings.DcRolePrefix + "one")
      }
      runOn(second, third) {
        pong.roles should contain(ClusterSettings.DcRolePrefix + "two")
      }

      enterBarrier("after-1")
    }

    "be able to use proxy across different data centers" in {
      runOn(third) {
        val proxy = system.actorOf(
          ClusterSingletonProxy.props(
            "/user/singletonManager",
            ClusterSingletonProxySettings(system).withRole(worker).withDataCenter("one")))
        proxy ! MultiDcSingleton.Ping
        val pong = expectMsgType[MultiDcSingleton.Pong](10.seconds)
        pong.fromDc should ===("one")
        pong.roles should contain(worker)
        pong.roles should contain(ClusterSettings.DcRolePrefix + "one")
      }
      enterBarrier("after-1")
    }

  }
}

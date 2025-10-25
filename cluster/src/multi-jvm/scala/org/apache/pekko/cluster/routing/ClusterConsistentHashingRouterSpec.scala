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

package org.apache.pekko.cluster.routing

import scala.concurrent.Await

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Address
import pekko.actor.Props
import pekko.cluster.MultiNodeClusterSpec
import pekko.pattern.ask
import pekko.remote.testkit.MultiNodeConfig
import pekko.routing.ActorRefRoutee
import pekko.routing.ConsistentHashingPool
import pekko.routing.ConsistentHashingRouter.ConsistentHashMapping
import pekko.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import pekko.routing.FromConfig
import pekko.routing.GetRoutees
import pekko.routing.Routees
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object ClusterConsistentHashingRouterMultiJvmSpec extends MultiNodeConfig {

  class Echo extends Actor {
    def receive = {
      case _ => sender() ! self
    }
  }

  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
      common-router-settings = {
        router = consistent-hashing-pool
        cluster {
          enabled = on
          max-nr-of-instances-per-node = 2
          max-total-nr-of-instances = 10
        }
      }

      pekko.actor.deployment {
        /router1 = $${common-router-settings}
        /router3 = $${common-router-settings}
        /router4 = $${common-router-settings}
      }
      """)).withFallback(MultiNodeClusterSpec.clusterConfig))

}

class ClusterConsistentHashingRouterMultiJvmNode1 extends ClusterConsistentHashingRouterSpec
class ClusterConsistentHashingRouterMultiJvmNode2 extends ClusterConsistentHashingRouterSpec
class ClusterConsistentHashingRouterMultiJvmNode3 extends ClusterConsistentHashingRouterSpec

abstract class ClusterConsistentHashingRouterSpec
    extends MultiNodeClusterSpec(ClusterConsistentHashingRouterMultiJvmSpec)
    with ImplicitSender
    with DefaultTimeout {
  import ClusterConsistentHashingRouterMultiJvmSpec._

  lazy val router1 = system.actorOf(FromConfig.props(Props[Echo]()), "router1")

  def currentRoutees(router: ActorRef) =
    Await.result(router ? GetRoutees, timeout.duration).asInstanceOf[Routees].routees

  /**
   * Fills in self address for local ActorRef
   */
  private def fullAddress(actorRef: ActorRef): Address = actorRef.path.address match {
    case Address(_, _, None, None) => cluster.selfAddress
    case a                         => a
  }

  "A cluster router with a consistent hashing pool" must {
    "start cluster with 2 nodes" in {
      awaitClusterUp(first, second)
      enterBarrier("after-1")
    }

    "create routees from configuration" in {
      runOn(first) {
        // it may take some time until router receives cluster member events
        awaitAssert { currentRoutees(router1).size should ===(4) }
        val routees = currentRoutees(router1)
        routees.collect { case ActorRefRoutee(ref) => fullAddress(ref) }.toSet should ===(
          Set(address(first), address(second)))
      }
      enterBarrier("after-2")
    }

    "select destination based on hashKey" in {
      runOn(first) {
        router1 ! ConsistentHashableEnvelope(message = "A", hashKey = "a")
        val destinationA = expectMsgType[ActorRef]
        router1 ! ConsistentHashableEnvelope(message = "AA", hashKey = "a")
        expectMsg(destinationA)
      }
      enterBarrier("after-2")
    }

    "deploy routees to new member nodes in the cluster" in {

      awaitClusterUp(first, second, third)

      runOn(first) {
        // it may take some time until router receives cluster member events
        awaitAssert { currentRoutees(router1).size should ===(6) }
        val routees = currentRoutees(router1)
        routees.collect { case ActorRefRoutee(ref) => fullAddress(ref) }.toSet should ===(roles.map(address).toSet)
      }

      enterBarrier("after-3")
    }

    "deploy programmatically defined routees to the member nodes in the cluster" in {
      runOn(first) {
        val router2 = system.actorOf(
          ClusterRouterPool(
            local = ConsistentHashingPool(nrOfInstances = 0),
            settings =
              ClusterRouterPoolSettings(totalInstances = 10, maxInstancesPerNode = 2, allowLocalRoutees = true))
            .props(Props[Echo]()),
          "router2")
        // it may take some time until router receives cluster member events
        awaitAssert { currentRoutees(router2).size should ===(6) }
        val routees = currentRoutees(router2)
        routees.collect { case ActorRefRoutee(ref) => fullAddress(ref) }.toSet should ===(roles.map(address).toSet)
      }

      enterBarrier("after-4")
    }

    "handle combination of configured router and programmatically defined hashMapping" in {
      runOn(first) {
        def hashMapping: ConsistentHashMapping = {
          case s: String => s
        }

        val router3 =
          system.actorOf(
            ConsistentHashingPool(nrOfInstances = 0, hashMapping = hashMapping).props(Props[Echo]()),
            "router3")

        assertHashMapping(router3)
      }

      enterBarrier("after-5")
    }

    "handle combination of configured router and programmatically defined hashMapping and ClusterRouterConfig" in {
      runOn(first) {
        def hashMapping: ConsistentHashMapping = {
          case s: String => s
        }

        val router4 =
          system.actorOf(
            ClusterRouterPool(
              local = ConsistentHashingPool(nrOfInstances = 0, hashMapping = hashMapping),
              settings =
                ClusterRouterPoolSettings(totalInstances = 10, maxInstancesPerNode = 1, allowLocalRoutees = true))
              .props(Props[Echo]()),
            "router4")

        assertHashMapping(router4)
      }

      enterBarrier("after-6")
    }

    def assertHashMapping(router: ActorRef): Unit = {
      // it may take some time until router receives cluster member events
      awaitAssert { currentRoutees(router).size should ===(6) }
      val routees = currentRoutees(router)
      routees.collect { case ActorRefRoutee(ref) => fullAddress(ref) }.toSet should ===(roles.map(address).toSet)

      router ! "a"
      val destinationA = expectMsgType[ActorRef]
      router ! "a"
      expectMsg(destinationA)
    }

  }
}

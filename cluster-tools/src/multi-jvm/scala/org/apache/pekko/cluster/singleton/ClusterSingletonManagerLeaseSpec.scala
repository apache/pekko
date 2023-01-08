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
import pekko.actor.{ Actor, ActorIdentity, ActorLogging, ActorRef, Address, Identify, PoisonPill, Props }
import pekko.cluster._
import pekko.cluster.MemberStatus.Up
import pekko.cluster.singleton.ClusterSingletonManagerLeaseSpec.ImportantSingleton.Response
import pekko.coordination.lease.TestLeaseActor
import pekko.coordination.lease.TestLeaseActorClient
import pekko.coordination.lease.TestLeaseActorClientExt
import pekko.remote.testkit.{ MultiNodeConfig, STMultiNodeSpec }
import pekko.testkit._

object ClusterSingletonManagerLeaseSpec extends MultiNodeConfig {
  val controller = role("controller")
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")

  testTransport(true)

  commonConfig(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
    pekko.cluster.testkit.auto-down-unreachable-after = 0s
    test-lease {
        lease-class = ${classOf[TestLeaseActorClient].getName}
        heartbeat-interval = 1s
        heartbeat-timeout = 120s
        lease-operation-timeout = 3s
   }
   pekko.cluster.singleton {
    use-lease = "test-lease"
   }
                                          """))

  nodeConfig(first, second, third)(ConfigFactory.parseString("pekko.cluster.roles = [worker]"))

  object ImportantSingleton {
    case class Response(msg: Any, address: Address) extends JavaSerializable

    def props(): Props = Props(new ImportantSingleton())
  }

  class ImportantSingleton extends Actor with ActorLogging {
    val selfAddress = Cluster(context.system).selfAddress
    override def preStart(): Unit = {
      log.info("Singleton starting")
    }
    override def postStop(): Unit = {
      log.info("Singleton stopping")
    }
    override def receive: Receive = {
      case msg =>
        sender() ! Response(msg, selfAddress)
    }
  }
}

class ClusterSingletonManagerLeaseMultiJvmNode1 extends ClusterSingletonManagerLeaseSpec
class ClusterSingletonManagerLeaseMultiJvmNode2 extends ClusterSingletonManagerLeaseSpec
class ClusterSingletonManagerLeaseMultiJvmNode3 extends ClusterSingletonManagerLeaseSpec
class ClusterSingletonManagerLeaseMultiJvmNode4 extends ClusterSingletonManagerLeaseSpec
class ClusterSingletonManagerLeaseMultiJvmNode5 extends ClusterSingletonManagerLeaseSpec

class ClusterSingletonManagerLeaseSpec
    extends MultiNodeClusterSpec(ClusterSingletonManagerLeaseSpec)
    with STMultiNodeSpec
    with ImplicitSender {

  import ClusterSingletonManagerLeaseSpec._
  import ClusterSingletonManagerLeaseSpec.ImportantSingleton._
  import TestLeaseActor._

  override def initialParticipants = roles.size

  // used on the controller
  val leaseProbe = TestProbe()

  "Cluster singleton manager with lease" should {

    "form a cluster" in {
      awaitClusterUp(controller, first)
      enterBarrier("initial-up")
      runOn(second) {
        within(10.seconds) {
          joinWithin(first)
          awaitAssert {
            cluster.state.members.toList.map(_.status) shouldEqual List(Up, Up, Up)
          }
        }
      }
      enterBarrier("second-up")
      runOn(third) {
        within(10.seconds) {
          joinWithin(first)
          awaitAssert {
            cluster.state.members.toList.map(_.status) shouldEqual List(Up, Up, Up, Up)
          }
        }
      }
      enterBarrier("third-up")
      runOn(fourth) {
        within(10.seconds) {
          joinWithin(first)
          awaitAssert {
            cluster.state.members.toList.map(_.status) shouldEqual List(Up, Up, Up, Up, Up)
          }
        }
      }
      enterBarrier("fourth-up")
    }

    "start test lease" in {
      runOn(controller) {
        system.actorOf(TestLeaseActor.props(), s"lease-${system.name}")
      }
      enterBarrier("lease-actor-started")
    }

    "find the lease on every node" in {
      system.actorSelection(node(controller) / "user" / s"lease-${system.name}") ! Identify(None)
      val leaseRef: ActorRef = expectMsgType[ActorIdentity].ref.get
      TestLeaseActorClientExt(system).setActorLease(leaseRef)
      enterBarrier("singleton-started")
    }

    "Start singleton and ping from all nodes" in {
      // fourth doesn't have the worker role
      runOn(first, second, third) {
        system.actorOf(
          ClusterSingletonManager
            .props(ImportantSingleton.props(), PoisonPill, ClusterSingletonManagerSettings(system).withRole("worker")),
          "important")
      }
      enterBarrier("singleton-started")

      val proxy = system.actorOf(
        ClusterSingletonProxy.props(
          singletonManagerPath = "/user/important",
          settings = ClusterSingletonProxySettings(system).withRole("worker")))

      runOn(first, second, third, fourth) {
        proxy ! "Ping"
        // lease has not been granted so now allowed to come up
        expectNoMessage(2.seconds)
      }

      enterBarrier("singleton-pending")

      runOn(controller) {
        TestLeaseActorClientExt(system).getLeaseActor() ! GetRequests
        expectMsg(LeaseRequests(List(Acquire(address(first).hostPort))))
        TestLeaseActorClientExt(system).getLeaseActor() ! ActionRequest(Acquire(address(first).hostPort), true)
      }
      enterBarrier("lease-acquired")

      runOn(first, second, third, fourth) {
        expectMsg(Response("Ping", address(first)))
      }
      enterBarrier("pinged")
    }

    "Move singleton when oldest node downed" in {

      cluster.state.members.size shouldEqual 5
      runOn(controller) {
        cluster.down(address(first))
        awaitAssert({
            cluster.state.members.toList.map(_.status) shouldEqual List(Up, Up, Up, Up)
          }, 20.seconds)
        val requests = awaitAssert({
            TestLeaseActorClientExt(system).getLeaseActor() ! GetRequests
            val msg = expectMsgType[LeaseRequests]
            withClue("Requests: " + msg) {
              msg.requests.size shouldEqual 2
            }
            msg
          }, 10.seconds)

        requests.requests should contain(Release(address(first).hostPort))
        requests.requests should contain(Acquire(address(second).hostPort))
      }
      runOn(second, third, fourth) {
        awaitAssert({
            cluster.state.members.toList.map(_.status) shouldEqual List(Up, Up, Up, Up)
          }, 20.seconds)
      }
      enterBarrier("first node downed")
      val proxy = system.actorOf(
        ClusterSingletonProxy.props(
          singletonManagerPath = "/user/important",
          settings = ClusterSingletonProxySettings(system).withRole("worker")))

      runOn(second, third, fourth) {
        proxy ! "Ping"
        // lease has not been granted so now allowed to come up
        expectNoMessage(2.seconds)
      }
      enterBarrier("singleton-not-migrated")

      runOn(controller) {
        TestLeaseActorClientExt(system).getLeaseActor() ! ActionRequest(Acquire(address(second).hostPort), true)
      }

      enterBarrier("singleton-moved-to-second")

      runOn(second, third, fourth) {
        proxy ! "Ping"
        expectMsg(Response("Ping", address(second)))
      }
      enterBarrier("finished")
    }
  }
}

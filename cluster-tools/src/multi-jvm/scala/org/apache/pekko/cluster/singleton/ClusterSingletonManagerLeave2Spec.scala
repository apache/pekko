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
import pekko.actor.ActorIdentity
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.Identify
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.testkit.STMultiNodeSpec
import pekko.testkit._

object ClusterSingletonManagerLeave2Spec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")
  val fifth = role("fifth")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
    pekko.cluster.testkit.auto-down-unreachable-after = off
    """))

  case object EchoStarted

  /**
   * The singleton actor
   */
  class Echo(testActor: ActorRef) extends Actor with ActorLogging {
    override def preStart(): Unit = {
      log.debug("Started singleton at [{}]", Cluster(context.system).selfAddress)
      testActor ! "preStart"
    }
    override def postStop(): Unit = {
      log.debug("Stopped singleton at [{}]", Cluster(context.system).selfAddress)
      testActor ! "postStop"
    }

    def receive = {
      case "stop" =>
        testActor ! "stop"
      // this is the stop message from singleton manager, but don't stop immediately
      // will be stopped via PoisonPill from the test to simulate delay
      case _ =>
        sender() ! self
    }
  }
}

class ClusterSingletonManagerLeave2MultiJvmNode1 extends ClusterSingletonManagerLeave2Spec
class ClusterSingletonManagerLeave2MultiJvmNode2 extends ClusterSingletonManagerLeave2Spec
class ClusterSingletonManagerLeave2MultiJvmNode3 extends ClusterSingletonManagerLeave2Spec
class ClusterSingletonManagerLeave2MultiJvmNode4 extends ClusterSingletonManagerLeave2Spec
class ClusterSingletonManagerLeave2MultiJvmNode5 extends ClusterSingletonManagerLeave2Spec

class ClusterSingletonManagerLeave2Spec
    extends MultiNodeSpec(ClusterSingletonManagerLeave2Spec)
    with STMultiNodeSpec
    with ImplicitSender {
  import ClusterSingletonManagerLeave2Spec._

  override def initialParticipants = roles.size

  lazy val cluster = Cluster(system)

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.join(node(to).address)
      createSingleton()
    }
  }

  def createSingleton(): ActorRef = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(classOf[Echo], testActor),
        terminationMessage = "stop",
        settings = ClusterSingletonManagerSettings(system)),
      name = "echo")
  }

  val echoProxyTerminatedProbe = TestProbe()

  lazy val echoProxy: ActorRef = {
    echoProxyTerminatedProbe.watch(
      system.actorOf(
        ClusterSingletonProxy
          .props(singletonManagerPath = "/user/echo", settings = ClusterSingletonProxySettings(system)),
        name = "echoProxy"))
  }

  "Leaving ClusterSingletonManager with two nodes" must {

    "hand-over to new instance" in {
      join(first, first)

      runOn(first) {
        within(5.seconds) {
          expectMsg("preStart")
          echoProxy ! "hello"
          expectMsgType[ActorRef]
        }
      }
      enterBarrier("first-active")

      join(second, first)
      runOn(first, second) {
        within(10.seconds) {
          awaitAssert(cluster.state.members.count(m => m.status == MemberStatus.Up) should be(2))
        }
      }
      enterBarrier("second-up")

      join(third, first)
      runOn(first, second, third) {
        within(10.seconds) {
          awaitAssert(cluster.state.members.count(m => m.status == MemberStatus.Up) should be(3))
        }
      }
      enterBarrier("third-up")
      runOn(first, second, third, fourth) {
        join(fourth, first)
        within(10.seconds) {
          awaitAssert(cluster.state.members.count(m => m.status == MemberStatus.Up) should be(4))
        }
      }
      enterBarrier("fourth-up")
      join(fifth, first)
      within(10.seconds) {
        awaitAssert(cluster.state.members.count(m => m.status == MemberStatus.Up) should be(5))
      }
      enterBarrier("all-up")

      runOn(first) {
        cluster.registerOnMemberRemoved(testActor ! "MemberRemoved")
        cluster.leave(cluster.selfAddress)
        expectMsg(10.seconds, "stop") // from singleton manager, but will not stop immediately
      }
      runOn(second, fourth) {
        cluster.registerOnMemberRemoved(testActor ! "MemberRemoved")
        cluster.leave(cluster.selfAddress)
        expectMsg(10.seconds, "MemberRemoved")
      }

      runOn(second, third) {
        (1 to 3).foreach { n =>
          Thread.sleep(1000)
          // singleton should not be started before old has been stopped
          system.actorSelection("/user/echo/singleton") ! Identify(n)
          expectMsg(ActorIdentity(n, None)) // not started
        }
      }
      enterBarrier("still-running-at-first")

      runOn(first) {
        system.actorSelection("/user/echo/singleton") ! PoisonPill
        expectMsg("postStop")
        // CoordinatedShutdown makes sure that singleton actors are
        // stopped before Cluster shutdown
        expectMsg(10.seconds, "MemberRemoved")
        echoProxyTerminatedProbe.expectTerminated(echoProxy, 10.seconds)
      }
      enterBarrier("stopped")

      runOn(third) {
        expectMsg(10.seconds, "preStart")
      }
      enterBarrier("third-started")

      runOn(third, fifth) {
        val p = TestProbe()
        val firstAddress = node(first).address
        p.within(15.seconds) {
          p.awaitAssert {
            echoProxy.tell("hello2", p.ref)
            p.expectMsgType[ActorRef](1.seconds).path.address should not be firstAddress

          }
        }
      }
      enterBarrier("third-working")
    }

  }
}

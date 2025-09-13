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

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.testkit.STMultiNodeSpec
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object ClusterSingletonManagerLeaveSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

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
  class Echo(testActor: ActorRef) extends Actor {
    override def preStart(): Unit = {
      testActor ! "preStart"
    }
    override def postStop(): Unit = {
      testActor ! "postStop"
    }

    def receive = {
      case "stop" =>
        testActor ! "stop"
        context.stop(self)
      case _ =>
        sender() ! self
    }
  }
}

class ClusterSingletonManagerLeaveMultiJvmNode1 extends ClusterSingletonManagerLeaveSpec
class ClusterSingletonManagerLeaveMultiJvmNode2 extends ClusterSingletonManagerLeaveSpec
class ClusterSingletonManagerLeaveMultiJvmNode3 extends ClusterSingletonManagerLeaveSpec

class ClusterSingletonManagerLeaveSpec
    extends MultiNodeSpec(ClusterSingletonManagerLeaveSpec)
    with STMultiNodeSpec
    with ImplicitSender {
  import ClusterSingletonManagerLeaveSpec._

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

  "Leaving ClusterSingletonManager" must {

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
      within(10.seconds) {
        awaitAssert(cluster.state.members.count(m => m.status == MemberStatus.Up) should be(3))
      }
      enterBarrier("all-up")

      runOn(second) {
        cluster.leave(node(first).address)
      }

      runOn(first) {
        cluster.registerOnMemberRemoved(testActor ! "MemberRemoved")
        expectMsg(10.seconds, "stop")
        expectMsg("postStop")
        // CoordinatedShutdown makes sure that singleton actors are
        // stopped before Cluster shutdown
        expectMsg("MemberRemoved")
        echoProxyTerminatedProbe.expectTerminated(echoProxy, 10.seconds)
      }
      enterBarrier("first-stopped")

      runOn(second) {
        expectMsg("preStart")
      }
      enterBarrier("second-started")

      runOn(second, third) {
        val p = TestProbe()
        val firstAddress = node(first).address
        p.within(15.seconds) {
          p.awaitAssert {
            echoProxy.tell("hello2", p.ref)
            p.expectMsgType[ActorRef](1.seconds).path.address should not be firstAddress

          }
        }
      }
      enterBarrier("second-working")

      runOn(second) {
        cluster.registerOnMemberRemoved(testActor ! "MemberRemoved")
        cluster.leave(node(second).address)
        expectMsg(15.seconds, "stop")
        expectMsg("postStop")
        expectMsg("MemberRemoved")
        echoProxyTerminatedProbe.expectTerminated(echoProxy, 10.seconds)
      }
      enterBarrier("second-stopped")

      runOn(third) {
        expectMsg("preStart")
      }
      enterBarrier("third-started")

      runOn(third) {
        cluster.registerOnMemberRemoved(testActor ! "MemberRemoved")
        cluster.leave(node(third).address)
        expectMsg(5.seconds, "stop")
        expectMsg("postStop")
        expectMsg("MemberRemoved")
        echoProxyTerminatedProbe.expectTerminated(echoProxy, 10.seconds)
      }
      enterBarrier("third-stopped")

    }

  }
}

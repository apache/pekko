/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import scala.collection.immutable.SortedSet

import org.apache.pekko
import pekko.actor.ActorSelection
import pekko.actor.Address
import pekko.actor.Props
import pekko.cluster.ClusterEvent.CurrentClusterState
import pekko.cluster.ClusterHeartbeatSender.Heartbeat
import pekko.cluster.CrossDcHeartbeatSender.ReportStatus
import pekko.cluster.CrossDcHeartbeatSenderSpec.TestCrossDcHeartbeatSender
import pekko.testkit.AkkaSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe
import pekko.util.Version

object CrossDcHeartbeatSenderSpec {
  class TestCrossDcHeartbeatSender(heartbeatProbe: TestProbe) extends CrossDcHeartbeatSender {
    // disable register for cluster events
    override def preStart(): Unit = {}

    override def heartbeatReceiver(address: Address): ActorSelection = {
      context.actorSelection(heartbeatProbe.ref.path)
    }
  }
}

class CrossDcHeartbeatSenderSpec extends AkkaSpec("""
    pekko.loglevel = DEBUG
    pekko.actor.provider = cluster
    # should not be used here
    pekko.cluster.failure-detector.heartbeat-interval = 5s
    pekko.cluster.multi-data-center {
      self-data-center = "dc1"
      failure-detector.heartbeat-interval = 0.2s
    }
  """) with ImplicitSender {
  "CrossDcHeartBeatSender" should {
    "increment heart beat sequence nr" in {

      val heartbeatProbe = TestProbe()
      Cluster(system).join(Cluster(system).selfMember.address)
      awaitAssert(Cluster(system).selfMember.status == MemberStatus.Up)
      val underTest = system.actorOf(Props(new TestCrossDcHeartbeatSender(heartbeatProbe)))

      underTest ! CurrentClusterState(
        members = SortedSet(
          Cluster(system).selfMember,
          Member(UniqueAddress(Address("akka", system.name), 2L), Set("dc-dc2"), Version.Zero)
            .copy(status = MemberStatus.Up)))

      awaitAssert {
        underTest ! ReportStatus()
        expectMsgType[CrossDcHeartbeatSender.MonitoringActive]
      }

      heartbeatProbe.expectMsgType[Heartbeat].sequenceNr shouldEqual 1
      heartbeatProbe.expectMsgType[Heartbeat].sequenceNr shouldEqual 2
    }
  }
}

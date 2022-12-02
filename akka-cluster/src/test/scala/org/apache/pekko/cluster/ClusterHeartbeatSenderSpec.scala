/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.actor.{ ActorSelection, Address, Props }
import pekko.cluster.ClusterEvent.{ CurrentClusterState, MemberUp }
import pekko.cluster.ClusterHeartbeatSender.Heartbeat
import pekko.cluster.ClusterHeartbeatSenderSpec.TestClusterHeartBeatSender
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }
import pekko.util.Version

object ClusterHeartbeatSenderSpec {
  class TestClusterHeartBeatSender(probe: TestProbe) extends ClusterHeartbeatSender {
    // don't register for cluster events
    override def preStart(): Unit = {}

    // override where the heart beats go to
    override def heartbeatReceiver(address: Address): ActorSelection = {
      context.actorSelection(probe.ref.path)
    }
  }
}

class ClusterHeartbeatSenderSpec extends PekkoSpec("""
    pekko.loglevel = DEBUG
    pekko.actor.provider = cluster 
    pekko.cluster.failure-detector.heartbeat-interval = 0.2s
  """.stripMargin) with ImplicitSender {

  "ClusterHeartBeatSender" must {
    "increment heart beat sequence nr" in {
      val probe = TestProbe()
      val underTest = system.actorOf(Props(new TestClusterHeartBeatSender(probe)))
      underTest ! CurrentClusterState()
      underTest ! MemberUp(
        Member(UniqueAddress(Address("akka", system.name), 1L), Set("dc-default"), Version.Zero)
          .copy(status = MemberStatus.Up))

      probe.expectMsgType[Heartbeat].sequenceNr shouldEqual 1
      probe.expectMsgType[Heartbeat].sequenceNr shouldEqual 2
    }
  }

}

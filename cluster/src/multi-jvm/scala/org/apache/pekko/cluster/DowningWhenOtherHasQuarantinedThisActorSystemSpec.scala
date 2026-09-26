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

package org.apache.pekko.cluster

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.Identify
import pekko.actor.RootActorPath
import pekko.remote.RARP
import pekko.remote.artery.ArterySettings
import pekko.remote.artery.ThisActorSystemQuarantinedEvent
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.ThrottlerTransportAdapter
import pekko.testkit.EventFilter
import pekko.testkit.LongRunningTest
import pekko.testkit.TestEvent.Mute

import com.typesafe.config.ConfigFactory

object DowningWhenOtherHasQuarantinedThisActorSystemSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")

  commonConfig(
    debugConfig(on = false)
      .withFallback(MultiNodeClusterSpec.clusterConfig)
      .withFallback(
        ConfigFactory.parseString("""
        pekko.remote.artery.enabled = on
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        pekko.cluster.split-brain-resolver.stable-after = 10s
        """)))

  testTransport(on = true)
}

class DowningWhenOtherHasQuarantinedThisActorSystemMultiJvmNode1
    extends DowningWhenOtherHasQuarantinedThisActorSystemSpec
class DowningWhenOtherHasQuarantinedThisActorSystemMultiJvmNode2
    extends DowningWhenOtherHasQuarantinedThisActorSystemSpec
class DowningWhenOtherHasQuarantinedThisActorSystemMultiJvmNode3
    extends DowningWhenOtherHasQuarantinedThisActorSystemSpec
class DowningWhenOtherHasQuarantinedThisActorSystemMultiJvmNode4
    extends DowningWhenOtherHasQuarantinedThisActorSystemSpec

abstract class DowningWhenOtherHasQuarantinedThisActorSystemSpec
    extends MultiNodeClusterSpec(DowningWhenOtherHasQuarantinedThisActorSystemSpec) {
  import DowningWhenOtherHasQuarantinedThisActorSystemSpec._

  muteDeadLetters(classOf[ActorIdentity])()
  system.eventStream.publish(Mute(EventFilter.info(pattern = ".*Ignoring received gossip from unknown.*")))

  "Cluster node downed by other" must {

    if (!ArterySettings(system.settings.config.getConfig("pekko.remote.artery")).Enabled) {
      // this feature only works in Artery, because classic remoting will not accept connections from
      // a quarantined node, and that is too high risk of introducing regressions if changing that
      pending
    }

    "join cluster" taggedAs LongRunningTest in {
      awaitClusterUp(first, second, third, fourth)
      enterBarrier("after-1")
    }

    "down itself with DownSelfQuarantinedByRemote when other has quarantined" taggedAs LongRunningTest in {
      runOn(first, second) {
        system.eventStream.subscribe(testActor, classOf[ThisActorSystemQuarantinedEvent])
      }
      runOn(first) {
        val secondUniqueAddress = cluster.state.members.find(_.address == address(second)).get.uniqueAddress
        RARP(system).provider
          .quarantine(secondUniqueAddress.address, Some(secondUniqueAddress.longUid), "Quarantine from test")
      }
      enterBarrier("quarantined")

      runOn(second) {
        within(5.seconds) { // this is shorter than split-brain-resolver.stable-after, so it's not normal downing
          awaitAssert {
            // try to ping first (Cluster Heartbeat messages will not trigger the Quarantine message)
            system.actorSelection(RootActorPath(first) / "user").tell(Identify(None), ActorRef.noSender)
            // shutting down itself triggered by ThisActorSystemQuarantinedEvent
            cluster.isTerminated should ===(true)
          }
        }
        expectMsgType[ThisActorSystemQuarantinedEvent]
      }
      enterBarrier("second-shutdown")

      runOn(first) {
        expectNoMessage(1.second) // no ThisActorSystemQuarantinedEvent
      }
      enterBarrier("wait")

      runOn(first) {
        val sel = system.actorSelection(RootActorPath(second) / "user")
        (1 to 15).foreach { _ =>
          sel.tell(Identify(None), ActorRef.noSender) // try to ping second
          expectNoMessage(200.millis) // no ThisActorSystemQuarantinedEvent
        }
      }

      enterBarrier("after-2")
    }

    "not be triggered by another node shutting down" taggedAs LongRunningTest in {
      runOn(first) {
        system.eventStream.subscribe(testActor, classOf[ThisActorSystemQuarantinedEvent])
      }
      enterBarrier("subscribing")

      runOn(third) {
        cluster.shutdown()
      }

      runOn(first) {
        expectNoMessage(1.second) // no ThisActorSystemQuarantinedEvent
      }
      enterBarrier("wait")

      runOn(first) {
        val sel = system.actorSelection(RootActorPath(third) / "user")
        (1 to 15).foreach { _ =>
          sel.tell(Identify(None), ActorRef.noSender) // try to ping third
          expectNoMessage(200.millis) // no ThisActorSystemQuarantinedEvent
        }
      }

      enterBarrier("after-3")
    }

    "not be triggered by another node shutting down during network partition" taggedAs LongRunningTest in {
      runOn(first) {
        system.eventStream.subscribe(testActor, classOf[ThisActorSystemQuarantinedEvent])
      }
      enterBarrier("subscribing")

      runOn(first) {
        testConductor.blackhole(first, fourth, ThrottlerTransportAdapter.Direction.Both).await
      }
      enterBarrier("blackhole")

      runOn(third) {
        cluster.shutdown()
      }

      runOn(first) {
        expectNoMessage(2.second) // no ThisActorSystemQuarantinedEvent
        testConductor.passThrough(first, fourth, ThrottlerTransportAdapter.Direction.Both).await
      }

      runOn(first) {
        expectNoMessage(1.second) // no ThisActorSystemQuarantinedEvent
      }
      enterBarrier("wait")

      runOn(first) {
        val sel = system.actorSelection(RootActorPath(fourth) / "user")
        (1 to 15).foreach { _ =>
          sel.tell(Identify(None), ActorRef.noSender) // try to ping fourth
          expectNoMessage(200.millis) // no ThisActorSystemQuarantinedEvent
        }
      }

      enterBarrier("after-4")
    }

  }
}

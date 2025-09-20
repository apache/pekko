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
import pekko.actor.ActorRef
import pekko.actor.Identify
import pekko.actor.RootActorPath
import pekko.remote.artery.ArterySettings
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.ThrottlerTransportAdapter
import pekko.testkit.LongRunningTest

import com.typesafe.config.ConfigFactory

object SplitBrainQuarantineSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")

  testTransport(on = true)
  commonConfig(
    debugConfig(on = true)
      .withFallback(MultiNodeClusterSpec.clusterConfig)
      .withFallback(ConfigFactory.parseString(
        """
        pekko.remote.artery.enabled = on
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        # we dont really want this to hit, but we need the sbr enabled to know the quarantining
        # downing does not trigger
        pekko.cluster.split-brain-resolver.stable-after = 5 minutes
        pekko.cluster.debug.verbose-gossip-logging = on
        """)))
}

class SplitBrainQuarantineMultiJvmNode1 extends SplitBrainQuarantineSpec
class SplitBrainQuarantineMultiJvmNode2 extends SplitBrainQuarantineSpec
class SplitBrainQuarantineMultiJvmNode3 extends SplitBrainQuarantineSpec
class SplitBrainQuarantineMultiJvmNode4 extends SplitBrainQuarantineSpec

abstract class SplitBrainQuarantineSpec extends MultiNodeClusterSpec(SplitBrainQuarantineSpec) {
  import SplitBrainQuarantineSpec._

  // reproduces the scenario where cluster is partitioned and each side (incorrectly) downs the other,
  // and after that the partition is resolved and the two split brain halves reconnects

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

    "split brain" taggedAs LongRunningTest in {
      runOn(first) {
        testConductor.blackhole(first, third, ThrottlerTransportAdapter.Direction.Both).await
        testConductor.blackhole(first, fourth, ThrottlerTransportAdapter.Direction.Both).await
        testConductor.blackhole(second, third, ThrottlerTransportAdapter.Direction.Both).await
        testConductor.blackhole(second, fourth, ThrottlerTransportAdapter.Direction.Both).await
      }
      enterBarrier("blackhole")
      system.log.info("cluster split into [JVM-1, JVM-2] and [JVM-3, JVM-4] with blackhole")

      within(15.seconds) {
        runOn(first) {
          cluster.down(third)
          cluster.down(fourth)
        }
        runOn(first, second) {
          awaitAssert {
            cluster.state.members.collect { case m if m.status == MemberStatus.Up => m.address } should ===(
              Set(address(first), address(second)))
            cluster.state.members.size should ===(2)
          }
          system.log.info("JVM-3 and JVM-4 downed from JVM-1")
        }

        runOn(third) {
          cluster.down(first)
          cluster.down(second)
        }
        runOn(third, fourth) {
          awaitAssert {
            cluster.state.members.collect { case m if m.status == MemberStatus.Up => m.address } should ===(
              Set(address(third), address(fourth)))
            cluster.state.members.size should ===(2)
          }
          system.log.info("JVM-1 and JVM-2 downed from JVM-3")
        }
      }
      enterBarrier("brain-split")

      runOn(first) {
        system.log.info("unblackholing cluster")
        testConductor.passThrough(first, third, ThrottlerTransportAdapter.Direction.Both).await
        testConductor.passThrough(first, fourth, ThrottlerTransportAdapter.Direction.Both).await
        testConductor.passThrough(second, third, ThrottlerTransportAdapter.Direction.Both).await
        testConductor.passThrough(second, fourth, ThrottlerTransportAdapter.Direction.Both).await
      }
      enterBarrier("unblackholed")

      // must send some actual messages that would trigger Quarantine to be sure it does in fact not happen
      runOn(first, second) {
        system.actorSelection(RootActorPath(third) / "user").tell(Identify(None), ActorRef.noSender)
        system.actorSelection(RootActorPath(fourth) / "user").tell(Identify(None), ActorRef.noSender)
      }
      runOn(third, fourth) {
        system.actorSelection(RootActorPath(first) / "user").tell(Identify(None), ActorRef.noSender)
        system.actorSelection(RootActorPath(second) / "user").tell(Identify(None), ActorRef.noSender)
      }
      Thread.sleep(3000)
      runOn(first, second) {
        system.actorSelection(RootActorPath(third) / "user").tell(Identify(None), ActorRef.noSender)
        system.actorSelection(RootActorPath(fourth) / "user").tell(Identify(None), ActorRef.noSender)
      }
      runOn(third, fourth) {
        system.actorSelection(RootActorPath(first) / "user").tell(Identify(None), ActorRef.noSender)
        system.actorSelection(RootActorPath(second) / "user").tell(Identify(None), ActorRef.noSender)
      }
      Thread.sleep(5000)
      enterBarrier("after-pass-through")

      // as the side that would quarantine each node is now not a part of the cluster it is the same as
      // a random node connecting and claiming a node is quarantined and therefore it cannot be trusted
      // enough to trigger a ThisActorSystemQuarantinedEvent-termination
      cluster.isTerminated should ===(false)
      enterBarrier("verify-alive")
    }

  }
}

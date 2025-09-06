/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.MemberStatus
import pekko.cluster.MemberStatus.Removed
import pekko.cluster.sharding.typed.ClusterShardingPreparingForShutdownSpec.Pinger.Command
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.cluster.typed.Leave
import pekko.cluster.typed.MultiNodeTypedClusterSpec
import pekko.cluster.typed.PrepareForFullClusterShutdown
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.serialization.jackson.CborSerializable
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object ClusterShardingPreparingForShutdownSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = DEBUG 
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
    pekko.cluster.testkit.auto-down-unreachable-after = off
    pekko.cluster.leader-actions-interval = 100ms
    """))

  object Pinger {
    sealed trait Command extends CborSerializable
    case class Ping(id: Int, ref: ActorRef[Pong]) extends Command
    case class Pong(id: Int) extends CborSerializable

    def apply(): Behavior[Command] = Behaviors.setup { _ =>
      Behaviors.receiveMessage[Command] {
        case Ping(id: Int, ref) =>
          ref ! Pong(id)
          Behaviors.same
      }
    }

  }

  val typeKey = EntityTypeKey[Command]("ping")
}

class ClusterShardingPreparingForShutdownMultiJvmNode1 extends ClusterShardingPreparingForShutdownSpec
class ClusterShardingPreparingForShutdownMultiJvmNode2 extends ClusterShardingPreparingForShutdownSpec
class ClusterShardingPreparingForShutdownMultiJvmNode3 extends ClusterShardingPreparingForShutdownSpec

class ClusterShardingPreparingForShutdownSpec
    extends MultiNodeSpec(ClusterShardingPreparingForShutdownSpec)
    with MultiNodeTypedClusterSpec {
  import ClusterShardingPreparingForShutdownSpec._
  import ClusterShardingPreparingForShutdownSpec.Pinger._

  override def initialParticipants = roles.size

  private val sharding = ClusterSharding(typedSystem)

  "Preparing for shut down ClusterSharding" must {

    "form cluster" in {
      formCluster(first, second, third)
    }

    "not rebalance but should still work preparing for shutdown" in {

      val shardRegion: ActorRef[ShardingEnvelope[Command]] =
        sharding.init(Entity(typeKey)(_ => Pinger()))

      val probe = TestProbe[Pong]()
      shardRegion ! ShardingEnvelope("id1", Pinger.Ping(1, probe.ref))
      probe.expectMessage(Pong(1))

      runOn(second) {
        cluster.manager ! PrepareForFullClusterShutdown
      }
      awaitAssert({
          withClue("members: " + cluster.state.members) {
            cluster.selfMember.status shouldEqual MemberStatus.ReadyForShutdown
            cluster.state.members.unsorted.map(_.status) shouldEqual Set(MemberStatus.ReadyForShutdown)
          }
        }, 10.seconds)
      enterBarrier("preparation-complete")

      shardRegion ! ShardingEnvelope("id2", Pinger.Ping(2, probe.ref))
      probe.expectMessage(Pong(2))

      runOn(second) {
        cluster.manager ! Leave(address(second))
      }
      awaitAssert(
        {
          runOn(first, third) {
            withClue("members: " + cluster.state.members) {
              cluster.state.members.size shouldEqual 2
            }
          }
          runOn(second) {
            withClue("self member: " + cluster.selfMember) {
              cluster.selfMember.status shouldEqual MemberStatus.Removed
            }
          }
        }, 5.seconds) // keep this lower than coordinated shutdown timeout

      // trigger creation of a new shard should be fine even though one node left
      runOn(first, third) {

        awaitAssert({
            shardRegion ! ShardingEnvelope("id3", Pinger.Ping(3, probe.ref))
            probe.expectMessage(Pong(3))
          }, 10.seconds)
      }
      enterBarrier("new-shards-verified")

      runOn(third) {
        cluster.manager ! Leave(address(first))
        cluster.manager ! Leave(address(third))
      }
      awaitAssert({
          withClue("self member: " + cluster.selfMember) {
            cluster.selfMember.status shouldEqual Removed
          }
        }, 15.seconds)
      enterBarrier("done")
    }
  }
}

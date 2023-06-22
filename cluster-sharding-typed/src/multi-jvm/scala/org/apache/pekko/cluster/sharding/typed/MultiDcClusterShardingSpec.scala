/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.cluster.MultiNodeClusterSpec
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.cluster.typed.{ MultiDcPinger, MultiNodeTypedClusterSpec }
import pekko.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import pekko.util.Timeout

object MultiDcClusterShardingSpecConfig extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")

  commonConfig(ConfigFactory.parseString("""
        pekko.loglevel = DEBUG
        pekko.cluster.sharding {
          number-of-shards = 10
          # First is likely to be ignored as shard coordinator not ready
          retry-interval = 0.2s
        }
      """).withFallback(MultiNodeClusterSpec.clusterConfig))

  nodeConfig(first, second)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc1"
    """))

  nodeConfig(third, fourth)(ConfigFactory.parseString("""
      pekko.cluster.multi-data-center.self-data-center = "dc2"
    """))

  testTransport(on = true)
}

class MultiDcClusterShardingMultiJvmNode1 extends MultiDcClusterShardingSpec
class MultiDcClusterShardingMultiJvmNode2 extends MultiDcClusterShardingSpec
class MultiDcClusterShardingMultiJvmNode3 extends MultiDcClusterShardingSpec
class MultiDcClusterShardingMultiJvmNode4 extends MultiDcClusterShardingSpec

abstract class MultiDcClusterShardingSpec
    extends MultiNodeSpec(MultiDcClusterShardingSpecConfig)
    with MultiNodeTypedClusterSpec
    with ScalaFutures {

  import MultiDcClusterShardingSpecConfig._
  import MultiDcPinger._

  override implicit def patienceConfig: PatienceConfig = {
    import pekko.testkit.TestDuration
    PatienceConfig(testKitSettings.DefaultTimeout.duration.dilated, 100.millis)
  }

  val typeKey = EntityTypeKey[Command]("ping")
  val entityId = "ping-1"

  "Cluster sharding in multi dc cluster" must {
    "form cluster" in {
      formCluster(first, second, third, fourth)
    }

    "init sharding" in {
      val sharding = ClusterSharding(typedSystem)
      val shardRegion: ActorRef[ShardingEnvelope[Command]] = sharding.init(Entity(typeKey)(_ => MultiDcPinger()))
      val probe = TestProbe[Pong]()
      shardRegion ! ShardingEnvelope(entityId, Ping(probe.ref))
      probe.expectMessage(max = 15.seconds, Pong(cluster.selfMember.dataCenter))
      enterBarrier("sharding-initialized")
    }

    "be able to message via entity ref" in {
      val probe = TestProbe[Pong]()
      val entityRef = ClusterSharding(typedSystem).entityRefFor(typeKey, entityId)
      entityRef ! Ping(probe.ref)
      probe.expectMessage(Pong(cluster.selfMember.dataCenter))
      enterBarrier("entity-ref")
    }
  }

  "be able to ask via entity ref" in {
    implicit val timeout = Timeout(remainingOrDefault)
    val entityRef = ClusterSharding(typedSystem).entityRefFor(typeKey, entityId)
    val response = entityRef.ask(Ping.apply)
    response.futureValue shouldEqual Pong(cluster.selfMember.dataCenter)
    enterBarrier("ask")
  }

  "be able to message cross dc via proxy, defined with ClusterShardingSettings" in {
    runOn(first, second) {
      val proxy: ActorRef[ShardingEnvelope[Command]] = ClusterSharding(typedSystem).init(
        Entity(typeKey)(_ => MultiDcPinger()).withSettings(ClusterShardingSettings(typedSystem).withDataCenter("dc2")))
      val probe = TestProbe[Pong]()
      proxy ! ShardingEnvelope(entityId, Ping(probe.ref))
      probe.expectMessage(remainingOrDefault, Pong("dc2"))
    }
    enterBarrier("cross-dc-1")
  }

  "be able to message cross dc via proxy, defined with Entity" in {
    runOn(first, second) {
      val system = typedSystem
      // #proxy-dc
      val proxy: ActorRef[ShardingEnvelope[Command]] =
        ClusterSharding(system).init(Entity(typeKey)(_ => MultiDcPinger()).withDataCenter("dc2"))
      // #proxy-dc
      val probe = TestProbe[Pong]()
      proxy ! ShardingEnvelope(entityId, Ping(probe.ref))
      probe.expectMessage(remainingOrDefault, Pong("dc2"))
    }
    enterBarrier("cross-dc-2")
  }

  "be able to message cross dc via proxy, defined with EntityRef" in {
    runOn(first, second) {
      val system = typedSystem
      // #proxy-dc-entityref
      // it must still be started before usage
      ClusterSharding(system).init(Entity(typeKey)(_ => MultiDcPinger()).withDataCenter("dc2"))

      val entityRef = ClusterSharding(system).entityRefFor(typeKey, entityId, "dc2")
      // #proxy-dc-entityref

      val probe = TestProbe[Pong]()
      entityRef ! Ping(probe.ref)
      probe.expectMessage(remainingOrDefault, Pong("dc2"))
    }
    enterBarrier("cross-dc-3")
  }
}

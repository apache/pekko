/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.WithLogCapturing

object ProxyShardingSpec {
  val config = """
  pekko.actor.provider = cluster
  pekko.loglevel = DEBUG
  pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
  pekko.remote.classic.netty.tcp.port = 0
  pekko.remote.artery.canonical.port = 0
  pekko.cluster.sharding.verbose-debug-logging = on
  pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
  """
}

class ProxyShardingSpec extends PekkoSpec(ProxyShardingSpec.config) with WithLogCapturing {

  val role = "Shard"
  val clusterSharding: ClusterSharding = ClusterSharding(system)
  val shardingSettings: ClusterShardingSettings =
    ClusterShardingSettings.create(system)
  val messageExtractor = new ShardRegion.HashCodeMessageExtractor(10) {
    override def entityId(message: Any) = "dummyId"
  }

  val idExtractor: ShardRegion.ExtractEntityId = {
    case msg => (msg.toString, msg)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case id: Int => id.toString
    case _       => throw new IllegalArgumentException()
  }

  val shardProxy: ActorRef =
    clusterSharding.startProxy("myType", Some(role), idExtractor, shardResolver)

  "Proxy should be found" in {
    val proxyActor: ActorRef = Await.result(
      system
        .actorSelection("akka://ProxyShardingSpec/system/sharding/myTypeProxy")
        .resolveOne(FiniteDuration(5, SECONDS)),
      3.seconds)

    proxyActor.path should not be null
    proxyActor.path.toString should endWith("Proxy")
  }

  "Shard region should be found" in {
    val shardRegion: ActorRef =
      clusterSharding.start("myType", TestActors.echoActorProps, shardingSettings, messageExtractor)

    shardRegion.path should not be null
    shardRegion.path.toString should endWith("myType")
  }

  "Shard coordinator should be found" in {
    val shardCoordinator: ActorRef =
      Await.result(
        system
          .actorSelection("akka://ProxyShardingSpec/system/sharding/myTypeCoordinator")
          .resolveOne(FiniteDuration(5, SECONDS)),
        3.seconds)

    shardCoordinator.path should not be null
    shardCoordinator.path.toString should endWith("Coordinator")
  }
}

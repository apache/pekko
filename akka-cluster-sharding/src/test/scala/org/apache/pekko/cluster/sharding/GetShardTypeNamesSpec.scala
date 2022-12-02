/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import org.apache.pekko
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors.EchoActor
import pekko.testkit.WithLogCapturing

object GetShardTypeNamesSpec {
  val config =
    """
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.cluster.sharding.fail-on-invalid-entity-state-transition = on
    """

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: Int => (msg.toString, msg)
    case _        => throw new IllegalArgumentException()
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: Int => (msg % 10).toString
    case _        => throw new IllegalArgumentException()
  }
}

class GetShardTypeNamesSpec extends PekkoSpec(GetShardTypeNamesSpec.config) with WithLogCapturing {
  import GetShardTypeNamesSpec._

  "GetShardTypeNames" must {
    "contain empty when join cluster without shards" in {
      ClusterSharding(system).shardTypeNames should ===(Set.empty[String])
    }

    "contain started shards when started 2 shards" in {
      Cluster(system).join(Cluster(system).selfAddress)
      val settings = ClusterShardingSettings(system)
      ClusterSharding(system).start("type1", Props[EchoActor](), settings, extractEntityId, extractShardId)
      ClusterSharding(system).start("type2", Props[EchoActor](), settings, extractEntityId, extractShardId)

      ClusterSharding(system).shardTypeNames should ===(Set("type1", "type2"))
    }
  }
}

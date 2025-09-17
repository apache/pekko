/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.external

import scala.concurrent.duration._

import org.apache.pekko
import pekko.cluster.sharding.external.ExternalShardAllocationStrategy.{
  GetShardLocation,
  GetShardLocationResponse,
  GetShardLocations
}
import pekko.testkit.{ PekkoSpec, TestProbe }
import pekko.util.Timeout

class ExternalShardAllocationStrategySpec extends PekkoSpec("""
    pekko.actor.provider = cluster 
    pekko.loglevel = INFO 
    pekko.remote.artery.canonical.port = 0
    """) {

  val requester = TestProbe()

  "ExternalShardAllocationClient" must {
    "default to no locations if sharding never started" in {
      ExternalShardAllocation(system)
        .clientFor("not found")
        .shardLocations()
        .futureValue
        .locations shouldEqual Map.empty
    }
  }

  "ExternalShardAllocation allocate" must {
    "default to requester if query times out" in {
      val (strat, _) = createStrategy()
      strat.allocateShard(requester.ref, "shard-1", Map.empty).futureValue shouldEqual requester.ref
    }
    "default to requester if no allocation" in {
      val (strat, probe) = createStrategy()
      val allocation = strat.allocateShard(requester.ref, "shard-1", Map.empty)
      probe.expectMsg(GetShardLocation("shard-1"))
      probe.reply(GetShardLocationResponse(None))
      allocation.futureValue shouldEqual requester.ref
    }
  }

  "ExternalShardAllocation rebalance" must {
    "default to no rebalance if query times out" in {
      val (strat, probe) = createStrategy()
      val rebalance = strat.rebalance(Map.empty, Set.empty)
      probe.expectMsg(GetShardLocations)
      rebalance.futureValue shouldEqual Set.empty
    }
  }

  def createStrategy(): (ExternalShardAllocationStrategy, TestProbe) = {
    val probe = TestProbe()
    val strategy = new ExternalShardAllocationStrategy(system, "type")(Timeout(250.millis)) {
      override private[pekko] def createShardStateActor() = probe.ref
    }
    strategy.start()
    (strategy, probe)
  }

}

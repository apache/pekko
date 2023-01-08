/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import org.apache.pekko.testkit._

object ClusterShardingIncorrectSetupSpecConfig
    extends MultiNodeClusterShardingConfig(
      additionalConfig = "pekko.cluster.sharding.waiting-for-state-timeout = 100ms") {

  val first = role("first")
  val second = role("second")

}

class ClusterShardingIncorrectSetupMultiJvmNode1 extends ClusterShardingIncorrectSetupSpec
class ClusterShardingIncorrectSetupMultiJvmNode2 extends ClusterShardingIncorrectSetupSpec

abstract class ClusterShardingIncorrectSetupSpec
    extends MultiNodeClusterShardingSpec(ClusterShardingIncorrectSetupSpecConfig)
    with ImplicitSender {

  import ClusterShardingIncorrectSetupSpecConfig._

  "Cluster sharding" must {
    "log useful error message if sharding not started" in {
      awaitClusterUp(roles: _*)
      enterBarrier("cluster-up")
      runOn(first) {
        EventFilter.error(pattern = """Has ClusterSharding been started on all nodes?""").intercept {
          startSharding(system, typeName = "Entity", entityProps = TestActors.echoActorProps)
        }
      }
      enterBarrier("helpful error message logged")
    }
  }
}

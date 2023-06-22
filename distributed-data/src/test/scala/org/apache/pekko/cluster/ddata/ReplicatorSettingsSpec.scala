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

package org.apache.pekko.cluster.ddata

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko.testkit.PekkoSpec

object ReplicatorSettingsSpec {

  val config = ConfigFactory.parseString("""
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.remote.artery.canonical.hostname = 127.0.0.1""")
}

class ReplicatorSettingsSpec
    extends PekkoSpec(ReplicatorSettingsSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  "DistributedData" must {
    "have the default replicator name" in {
      ReplicatorSettings.name(system, None) should ===("ddataReplicator")
    }
    "have the prefixed replicator name" in {
      ReplicatorSettings.name(system, Some("other")) should ===("otherDdataReplicator")
    }
  }
}

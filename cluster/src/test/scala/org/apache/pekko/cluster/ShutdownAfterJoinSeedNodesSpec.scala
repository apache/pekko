/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.testkit._

object ShutdownAfterJoinSeedNodesSpec {

  val config = """
       pekko.actor.provider = "cluster"
       pekko.coordinated-shutdown.terminate-actor-system = on
       pekko.remote.classic.netty.tcp.port = 0
       pekko.remote.artery.canonical.port = 0
       pekko.cluster {
         seed-node-timeout = 2s
         retry-unsuccessful-join-after = 2s
         shutdown-after-unsuccessful-join-seed-nodes = 5s
         jmx.multi-mbeans-in-same-jvm = on
       }
       """
}

class ShutdownAfterJoinSeedNodesSpec extends PekkoSpec(ShutdownAfterJoinSeedNodesSpec.config) {

  val seed1 = ActorSystem(system.name, system.settings.config)
  val seed2 = ActorSystem(system.name, system.settings.config)
  val oridinary1 = ActorSystem(system.name, system.settings.config)

  override protected def afterTermination(): Unit = {
    shutdown(seed1)
    shutdown(seed2)
    shutdown(oridinary1)
  }

  "Joining seed nodes" must {
    "be aborted after shutdown-after-unsuccessful-join-seed-nodes" taggedAs LongRunningTest in {

      val seedNodes: immutable.IndexedSeq[Address] = Vector(seed1, seed2).map(s => Cluster(s).selfAddress)
      shutdown(seed1) // crash so that others will not be able to join

      Cluster(seed2).joinSeedNodes(seedNodes)
      Cluster(oridinary1).joinSeedNodes(seedNodes)

      Await.result(seed2.whenTerminated, Cluster(seed2).settings.ShutdownAfterUnsuccessfulJoinSeedNodes + 10.second)
      Await.result(
        oridinary1.whenTerminated,
        Cluster(seed2).settings.ShutdownAfterUnsuccessfulJoinSeedNodes + 10.second)
    }

  }
}

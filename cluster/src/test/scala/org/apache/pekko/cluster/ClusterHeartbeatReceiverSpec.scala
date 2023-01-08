/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.cluster.ClusterHeartbeatSender.{ Heartbeat, HeartbeatRsp }
import pekko.testkit.{ ImplicitSender, PekkoSpec }

class ClusterHeartbeatReceiverSpec extends PekkoSpec("""
    pekko.actor.provider = cluster 
  """.stripMargin) with ImplicitSender {
  "ClusterHeartbeatReceiver" should {
    "respond to heartbeats with the same sequenceNr and sendTime" in {
      val heartBeater = system.actorOf(ClusterHeartbeatReceiver.props(() => Cluster(system)))
      heartBeater ! Heartbeat(Cluster(system).selfAddress, 1, 2)
      expectMsg(HeartbeatRsp(Cluster(system).selfUniqueAddress, 1, 2))
    }
  }
}

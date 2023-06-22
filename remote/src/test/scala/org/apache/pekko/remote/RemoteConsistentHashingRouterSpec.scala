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

package org.apache.pekko.remote

import org.apache.pekko
import pekko.actor.Address
import pekko.routing.ActorSelectionRoutee
import pekko.routing.ConsistentHash
import pekko.routing.ConsistentRoutee
import pekko.testkit.PekkoSpec

class RemoteConsistentHashingRouterSpec
    extends PekkoSpec("""
    pekko.remote.artery.canonical.port = 0                                                         
    pekko.actor.provider = remote """) {

  "ConsistentHashingGroup" must {

    "use same hash ring independent of self address" in {
      // simulating running router on two different nodes (a1, a2) with target routees on 3 other nodes (s1, s2, s3)
      val a1 = Address("pekko", "Sys", "client1", 7355)
      val a2 = Address("pekko", "Sys", "client2", 7355)
      val s1 = ActorSelectionRoutee(system.actorSelection("pekko://Sys@server1:7355/user/a/b"))
      val s2 = ActorSelectionRoutee(system.actorSelection("pekko://Sys@server2:7355/user/a/b"))
      val s3 = ActorSelectionRoutee(system.actorSelection("pekko://Sys@server3:7355/user/a/b"))
      val nodes1 = List(ConsistentRoutee(s1, a1), ConsistentRoutee(s2, a1), ConsistentRoutee(s3, a1))
      val nodes2 = List(ConsistentRoutee(s1, a2), ConsistentRoutee(s2, a2), ConsistentRoutee(s3, a2))
      val consistentHash1 = ConsistentHash(nodes1, 10)
      val consistentHash2 = ConsistentHash(nodes2, 10)
      val keys = List("A", "B", "C", "D", "E", "F", "G")
      val result1 = keys.collect { case k => consistentHash1.nodeFor(k).routee }
      val result2 = keys.collect { case k => consistentHash2.nodeFor(k).routee }
      result1 should ===(result2)
    }

  }

}

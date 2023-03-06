/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.actor.Address

class HeartbeatNodeRingSpec extends AnyWordSpec with Matchers {

  val aa = UniqueAddress(Address("pekko", "sys", "aa", 7355), 1L)
  val bb = UniqueAddress(Address("pekko", "sys", "bb", 7355), 2L)
  val cc = UniqueAddress(Address("pekko", "sys", "cc", 7355), 3L)
  val dd = UniqueAddress(Address("pekko", "sys", "dd", 7355), 4L)
  val ee = UniqueAddress(Address("pekko", "sys", "ee", 7355), 5L)
  val ff = UniqueAddress(Address("pekko", "sys", "ff", 7355), 6L)

  val nodes = Set(aa, bb, cc, dd, ee, ff)

  "A HashedNodeRing" must {

    "pick specified number of nodes as receivers" in {
      val ring = HeartbeatNodeRing(cc, nodes, Set.empty, 3)
      ring.myReceivers should ===(ring.receivers(cc))

      nodes.foreach { n =>
        val receivers = ring.receivers(n)
        receivers.size should ===(3)
        receivers should not contain n
      }
    }

    "pick specified number of nodes + unreachable as receivers" in {
      val ring = HeartbeatNodeRing(cc, nodes, unreachable = Set(aa, dd, ee), monitoredByNrOfMembers = 3)
      ring.myReceivers should ===(ring.receivers(cc))

      ring.receivers(aa) should ===(Set(bb, cc, dd, ff)) // unreachable ee skipped
      ring.receivers(bb) should ===(Set(cc, dd, ee, ff)) // unreachable aa skipped
      ring.receivers(cc) should ===(Set(dd, ee, ff, bb)) // unreachable aa skipped
      ring.receivers(dd) should ===(Set(ee, ff, aa, bb, cc))
      ring.receivers(ee) should ===(Set(ff, aa, bb, cc))
      ring.receivers(ff) should ===(Set(aa, bb, cc)) // unreachable dd and ee skipped
    }

    "pick all except own as receivers when less than total number of nodes" in {
      val expected = Set(aa, bb, dd, ee, ff)
      HeartbeatNodeRing(cc, nodes, Set.empty, 5).myReceivers should ===(expected)
      HeartbeatNodeRing(cc, nodes, Set.empty, 6).myReceivers should ===(expected)
      HeartbeatNodeRing(cc, nodes, Set.empty, 7).myReceivers should ===(expected)
    }

    "pick none when alone" in {
      val ring = HeartbeatNodeRing(cc, Set(cc), Set.empty, 3)
      ring.myReceivers should ===(Set())
    }

  }
}

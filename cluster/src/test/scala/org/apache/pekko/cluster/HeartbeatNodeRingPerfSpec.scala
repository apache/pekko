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

class HeartbeatNodeRingPerfSpec extends AnyWordSpec with Matchers {

  val nodesSize = sys.props.get("org.apache.pekko.cluster.HeartbeatNodeRingPerfSpec.nodesSize").getOrElse("250").toInt
  // increase for serious measurements
  val iterations =
    sys.props.get("org.apache.pekko.cluster.HeartbeatNodeRingPerfSpec.iterations").getOrElse("1000").toInt

  def createHeartbeatNodeRingOfSize(size: Int): HeartbeatNodeRing = {
    val nodes = (1 to size).map(n => UniqueAddress(Address("pekko", "sys", "node-" + n, 7355), n.toLong))
    val selfAddress = nodes(size / 2)
    HeartbeatNodeRing(selfAddress, nodes.toSet, Set.empty, 5)
  }

  val heartbeatNodeRing = createHeartbeatNodeRingOfSize(nodesSize)

  private def checkThunkForRing(ring: HeartbeatNodeRing, thunk: HeartbeatNodeRing => Unit, times: Int): Unit =
    for (_ <- 1 to times) thunk(ring)

  private def myReceivers(ring: HeartbeatNodeRing): Unit = {
    val r = HeartbeatNodeRing(ring.selfAddress, ring.nodes, Set.empty, ring.monitoredByNrOfMembers)
    r.myReceivers.isEmpty should ===(false)
  }

  s"HeartbeatNodeRing of size $nodesSize" must {

    s"do a warm up run, $iterations times" in {
      checkThunkForRing(heartbeatNodeRing, myReceivers, iterations)
    }

    s"produce myReceivers, $iterations times" in {
      checkThunkForRing(heartbeatNodeRing, myReceivers, iterations)
    }

  }

}

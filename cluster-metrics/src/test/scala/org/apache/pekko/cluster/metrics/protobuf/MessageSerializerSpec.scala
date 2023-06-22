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

package org.apache.pekko.cluster.metrics.protobuf

import org.apache.pekko
import pekko.actor.{ Address, ExtendedActorSystem }
import pekko.cluster.MemberStatus
import pekko.cluster.TestMember
import pekko.cluster.metrics._
import pekko.testkit.PekkoSpec

class MessageSerializerSpec extends PekkoSpec("""
     pekko.actor.provider = cluster
     pekko.remote.classic.netty.tcp.port = 0
     pekko.remote.artery.canonical.port = 0
  """) {

  val serializer = new MessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  def checkSerialization(obj: AnyRef): Unit = {
    val blob = serializer.toBinary(obj)
    val ref = serializer.fromBinary(blob, serializer.manifest(obj))
    obj match {
      case _ =>
        ref should ===(obj)
    }

  }

  import MemberStatus._

  val a1 = TestMember(Address("pekko", "sys", "a", 7355), Joining, Set.empty)
  val b1 = TestMember(Address("pekko", "sys", "b", 7355), Up, Set("r1"))
  val c1 = TestMember(Address("pekko", "sys", "c", 7355), Leaving, Set("r2"))
  val d1 = TestMember(Address("pekko", "sys", "d", 7355), Exiting, Set("r1", "r2"))
  val e1 = TestMember(Address("pekko", "sys", "e", 7355), Down, Set("r3"))
  val f1 = TestMember(Address("pekko", "sys", "f", 7355), Removed, Set("r2", "r3"))

  "ClusterMessages" must {

    "be serializable" in {

      val metricsGossip = MetricsGossip(
        Set(
          NodeMetrics(a1.address, 4711, Set(Metric("foo", 1.2, None))),
          NodeMetrics(
            b1.address,
            4712,
            Set(
              Metric("foo", 2.1, Some(EWMA(value = 100.0, alpha = 0.18))),
              Metric("bar1", Double.MinPositiveValue, None),
              Metric("bar2", Float.MaxValue, None),
              Metric("bar3", Int.MaxValue, None),
              Metric("bar4", Long.MaxValue, None),
              Metric("bar5", BigInt(Long.MaxValue), None)))))

      checkSerialization(MetricsGossipEnvelope(a1.address, metricsGossip, true))

    }
  }

  "AdaptiveLoadBalancingPool" must {
    "be serializable" in {
      val simplePool = AdaptiveLoadBalancingPool()
      checkSerialization(simplePool)

      val complicatedPool = AdaptiveLoadBalancingPool(
        metricsSelector =
          MixMetricsSelector(Vector(CpuMetricsSelector, HeapMetricsSelector, SystemLoadAverageMetricsSelector)),
        nrOfInstances = 7,
        routerDispatcher = "my-dispatcher",
        usePoolDispatcher = true)
      checkSerialization(complicatedPool)
    }
  }
}

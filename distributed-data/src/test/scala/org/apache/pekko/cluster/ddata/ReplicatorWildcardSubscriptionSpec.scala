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

package org.apache.pekko.cluster.ddata

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.cluster.ddata.Replicator._
import pekko.testkit.ImplicitSender
import pekko.testkit.TestKit
import pekko.testkit.TestProbe

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object ReplicatorWildcardSubscriptionSpec {
  val config = ConfigFactory.parseString("""
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.remote.artery.canonical.hostname = 127.0.0.1
    pekko.cluster.distributed-data.notify-subscribers-interval = 100ms
    """)
}

class ReplicatorWildcardSubscriptionSpec(_system: ActorSystem)
    extends TestKit(_system)
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  def this() =
    this(ActorSystem("ReplicatorWildcardSubscriptionSpec", ReplicatorWildcardSubscriptionSpec.config))

  override def afterAll(): Unit = shutdown(system)

  implicit val selfUniqueAddress: SelfUniqueAddress = DistributedData(system).selfUniqueAddress
  val replicator = DistributedData(system).replicator

  "Replicator wildcard subscriptions" must {

    "notify subscriber for keys matching the wildcard prefix" in {
      val KeyA1 = GCounterKey("notif-counter-a1")
      val KeyA2 = GCounterKey("notif-counter-a2")
      val KeyB = GCounterKey("notif-other-counter")
      val WildcardKey = GCounterKey("notif-counter-*")

      val probe = TestProbe()
      replicator ! Subscribe(WildcardKey, probe.ref)

      // Update a matching key
      replicator ! Update(KeyA1, GCounter.empty, WriteLocal)(_ :+ 1)
      expectMsgType[UpdateSuccess[_]]

      val changed1 = probe.expectMsgType[Changed[GCounter]](5.seconds)
      changed1.key.id should ===("notif-counter-a1")
      changed1.get(changed1.key).value should ===(1)

      // Update another matching key
      replicator ! Update(KeyA2, GCounter.empty, WriteLocal)(_ :+ 2)
      expectMsgType[UpdateSuccess[_]]

      val changed2 = probe.expectMsgType[Changed[GCounter]](5.seconds)
      changed2.key.id should ===("notif-counter-a2")
      changed2.get(changed2.key).value should ===(2)

      // Update a non-matching key - no notification expected
      replicator ! Update(KeyB, GCounter.empty, WriteLocal)(_ :+ 1)
      expectMsgType[UpdateSuccess[_]]

      probe.expectNoMessage(500.millis)
    }

    "send current value to new wildcard subscriber for existing matching keys" in {
      val KeyA = GCounterKey("current-counter-a")
      val WildcardKey = GCounterKey("current-counter-*")

      replicator ! Update(KeyA, GCounter.empty, WriteLocal)(_ :+ 10)
      expectMsgType[UpdateSuccess[_]]

      // Subscribe after the key already exists
      val subscribeProbe = TestProbe()
      replicator ! Subscribe(WildcardKey, subscribeProbe.ref)

      // Should receive current value for existing matching key
      val changed = subscribeProbe.expectMsgType[Changed[GCounter]](5.seconds)
      changed.key.id should ===("current-counter-a")
      changed.get(changed.key).value should ===(10)
    }

    "unsubscribe wildcard subscriber" in {
      val KeyA = GCounterKey("unsub-counter-a")
      val WildcardKey = GCounterKey("unsub-counter-*")

      replicator ! Update(KeyA, GCounter.empty, WriteLocal)(_ :+ 1)
      expectMsgType[UpdateSuccess[_]]

      val probe = TestProbe()
      replicator ! Subscribe(WildcardKey, probe.ref)

      // Receive the initial value
      probe.expectMsgType[Changed[GCounter]](5.seconds)

      // Unsubscribe
      replicator ! Unsubscribe(WildcardKey, probe.ref)

      // Update a matching key - no notification expected after unsubscribe
      replicator ! Update(KeyA, GCounter.empty, WriteLocal)(_ :+ 100)
      expectMsgType[UpdateSuccess[_]]

      probe.expectNoMessage(500.millis)
    }
  }
}

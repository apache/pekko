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

package org.apache.pekko.cluster.typed.internal.receptionist

import scala.concurrent.duration._
import scala.concurrent.duration.Deadline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.Address
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.internal.receptionist.AbstractServiceKey
import pekko.actor.typed.receptionist.ServiceKey
import pekko.cluster.UniqueAddress
import pekko.cluster.typed.internal.receptionist.ClusterReceptionistProtocol.SubscriptionsKV
import pekko.util.TypedMultiMap

class ClusterReceptionistStateSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {

  val SomeService = ServiceKey[Int]("boy-oh-boy!")
  val SomeOtherService = ServiceKey[Int]("disappointing!")

  private def emptyState(
      distributedKeyCount: Int = 1,
      self: UniqueAddress = UniqueAddress(Address("127.0.0.1", "MySystem"), 555L)) =
    ClusterReceptionist.State(
      registry = ShardedServiceRegistry(distributedKeyCount).addNode(self),
      servicesPerActor = Map.empty,
      tombstones = Map.empty,
      subscriptions = TypedMultiMap.empty[AbstractServiceKey, SubscriptionsKV])

  "The internal ClusterReceptionist State" must {

    "keep track of local keys per service" in {
      val someRef = createTestProbe[Int]().ref
      var state = emptyState()
      state = state.addLocalService(someRef, SomeService)
      state = state.addLocalService(someRef, SomeOtherService)
      state.servicesPerActor(someRef) should ===(Set(SomeService, SomeOtherService))
      state = state.removeLocalService(someRef, SomeService, Deadline.now)
      state = state.removeLocalService(someRef, SomeOtherService, Deadline.now)
      state.servicesPerActor.get(someRef) should ===(None)
    }

    "keep a tombstone for removed services" in {
      val someRef = createTestProbe[Int]().ref
      var state = emptyState()
      state = state.addLocalService(someRef, SomeService)
      state = state.removeLocalService(someRef, SomeService, Deadline.now)
      state.hasTombstone(SomeService)(someRef) should ===(true)
    }

    "prune tombstones" in {
      val someRef = createTestProbe[Int]().ref
      var state = emptyState()
      state = state.addLocalService(someRef, SomeService)
      state = state.removeLocalService(someRef, SomeService, Deadline.now - (10.seconds))
      state = state.pruneTombstones()
      state.tombstones shouldBe empty
    }

  }

}

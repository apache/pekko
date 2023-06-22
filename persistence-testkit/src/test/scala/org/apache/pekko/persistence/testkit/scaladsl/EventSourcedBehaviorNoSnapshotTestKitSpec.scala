/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKitSpec.TestCounter
import pekko.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class EventSourcedBehaviorNoSnapshotTestKitSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.parseString("""
    pekko.persistence.testkit.events.serialize = off
    pekko.persistence.testkit.snapshots.serialize = off
    """).withFallback(PersistenceTestKitPlugin.config))
    with AnyWordSpecLike
    with LogCapturing {

  private def createTestKit() = {
    EventSourcedBehaviorTestKit[TestCounter.Command, TestCounter.Event, TestCounter.State](
      system,
      TestCounter(PersistenceId.ofUniqueId("test")))
  }

  "EventSourcedBehaviorTestKit" when {
    "snapshots are not enabled" must {
      "not provide SnapshotTestKit" in {
        val eventSourcedTestKit = createTestKit()

        eventSourcedTestKit.snapshotTestKit shouldBe empty
      }

      "fail initializing from snapshot" in {
        val eventSourcedTestKit = createTestKit()

        val ex = intercept[IllegalArgumentException] {
          eventSourcedTestKit.initialize(TestCounter.RealState(1, Vector(0)))
        }
        ex.getMessage shouldEqual "Cannot initialize from state when snapshots are not used."
      }

      "initialize from event" in {
        val eventSourcedTestKit = createTestKit()
        eventSourcedTestKit.initialize(TestCounter.Incremented(1))

        val result = eventSourcedTestKit.runCommand[TestCounter.State](TestCounter.GetValue(_))
        result.reply shouldEqual TestCounter.RealState(1, Vector(0))
      }
    }
  }
}

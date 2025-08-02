/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.TestKitSettings
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.typed.PersistenceId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

object NullEmptyStateSpec {

  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))
}

class NullEmptyStateSpec
    extends ScalaTestWithActorTestKit(NullEmptyStateSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  implicit val testSettings: TestKitSettings = TestKitSettings(system)

  def nullState(persistenceId: PersistenceId, probe: ActorRef[String]): Behavior[String] =
    DurableStateBehavior[String, String](
      persistenceId,
      emptyState = null,
      commandHandler = (state, command) => {
        if (command == "stop")
          Effect.stop()
        else if (state == null)
          Effect.persist(command).thenReply(probe)(newState => newState)
        else
          Effect.persist(s"$state:$command").thenReply(probe)(newState => newState)
      })

  "A typed persistent actor with null empty state" must {
    "persist and update state" in {
      val probe = TestProbe[String]()
      val b = nullState(PersistenceId.ofUniqueId("a"), probe.ref)
      val ref1 = spawn(b)
      ref1 ! "one"
      probe.expectMessage("one")
      ref1 ! "two"
      probe.expectMessage("one:two")
      ref1 ! "stop"
      probe.expectTerminated(ref1)

      val ref2 = spawn(b)
      ref2 ! "three"
      probe.expectMessage("one:two:three")
    }
  }
}

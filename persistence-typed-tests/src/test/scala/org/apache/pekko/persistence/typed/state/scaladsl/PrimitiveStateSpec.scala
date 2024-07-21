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
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.typed.PersistenceId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

object PrimitiveStateSpec {

  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))
}

class PrimitiveStateSpec
    extends ScalaTestWithActorTestKit(PrimitiveStateSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  def primitiveState(persistenceId: PersistenceId, probe: ActorRef[String]): Behavior[Int] =
    DurableStateBehavior[Int, Int](
      persistenceId,
      emptyState = 0,
      commandHandler = (state, command) =>
        if (command < 0)
          Effect.stop()
        else
          Effect.persist(state + command).thenReply(probe)(newState => newState.toString)
    )

  "A typed persistent actor with primitive state" must {
    "persist primitive state and update" in {
      val probe = TestProbe[String]()
      val b = primitiveState(PersistenceId.ofUniqueId("a"), probe.ref)
      val ref1 = spawn(b)
      ref1 ! 1
      probe.expectMessage("1")
      ref1 ! 2
      probe.expectMessage("3")

      ref1 ! -1
      probe.expectTerminated(ref1)

      val ref2 = spawn(b)
      ref2 ! 3
      probe.expectMessage("6")
    }
  }
}

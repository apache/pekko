/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

object PrimitiveStateSpec {

  private val conf = ConfigFactory.parseString(s"""
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.journal.inmem.test-serialization = on
    """)
}

class PrimitiveStateSpec
    extends ScalaTestWithActorTestKit(PrimitiveStateSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  def primitiveState(persistenceId: PersistenceId, probe: ActorRef[String]): Behavior[Int] =
    EventSourcedBehavior[Int, Int, Int](
      persistenceId,
      emptyState = 0,
      commandHandler = (_, command) => {
        if (command < 0)
          Effect.stop()
        else
          Effect.persist(command)
      },
      eventHandler = (state, event) => {
        probe.tell("eventHandler:" + state + ":" + event)
        state + event
      }).receiveSignal {
      case (n, RecoveryCompleted) =>
        probe.tell("onRecoveryCompleted:" + n)
    }

  "A typed persistent actor with primitive state" must {
    "persist primitive events and update state" in {
      val probe = TestProbe[String]()
      val b = primitiveState(PersistenceId.ofUniqueId("a"), probe.ref)
      val ref1 = spawn(b)
      probe.expectMessage("onRecoveryCompleted:0")
      ref1 ! 1
      probe.expectMessage("eventHandler:0:1")
      ref1 ! 2
      probe.expectMessage("eventHandler:1:2")

      ref1 ! -1
      probe.expectTerminated(ref1)

      val ref2 = spawn(b)
      // eventHandler from replay
      probe.expectMessage("eventHandler:0:1")
      probe.expectMessage("eventHandler:1:2")
      probe.expectMessage("onRecoveryCompleted:3")
      ref2 ! 3
      probe.expectMessage("eventHandler:3:3")
    }

  }
}

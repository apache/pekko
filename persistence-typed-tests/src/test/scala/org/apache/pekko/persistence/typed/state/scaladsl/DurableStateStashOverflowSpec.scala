/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.scaladsl

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.adapter._
import pekko.persistence.journal.SteppingInmemJournal
import pekko.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

// Reproducer for #1327
object DurableStateStashOverflowSpec {

  object DurableStateStringList {
    sealed trait Command
    case class DoNothing(replyTo: ActorRef[Done]) extends Command

    def apply(persistenceId: PersistenceId): Behavior[Command] =
      DurableStateBehavior[Command, List[String]](
        persistenceId,
        Nil,
        { (_, command) =>
          command match {
            case DoNothing(replyTo) =>
              Effect.persist(List.empty[String]).thenRun(_ => replyTo ! Done)
          }
        })
  }

  def conf =
    SteppingInmemJournal.config("DurableStateStashOverflow").withFallback(ConfigFactory.parseString(s"""
       pekko.persistence {
         state.plugin = "pekko.persistence.journal.stepping-inmem"
         typed {
           stash-capacity = 20000 # enough to fail on stack size
           stash-overflow-strategy = "drop"
         }
       }
       pekko.jvm-exit-on-fatal-error = off
   """))
}

class DurableStateStashOverflowSpec
    extends ScalaTestWithActorTestKit(DurableStateStashOverflowSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import DurableStateStashOverflowSpec.DurableStateStringList

  "Stashing in a busy durable state behavior" must {

    "not cause stack overflow" in {
      val es = spawn(DurableStateStringList(PersistenceId.ofUniqueId("id-1")))

      // wait for journal to start
      val probe = testKit.createTestProbe[Done]()
      probe.awaitAssert(SteppingInmemJournal.getRef("DurableStateStashOverflow"), 3.seconds)
      val journal = SteppingInmemJournal.getRef("DurableStateStashOverflow")

      val droppedMessageProbe = testKit.createDroppedMessageProbe()
      val stashCapacity = testKit.config.getInt("pekko.persistence.typed.stash-capacity")

      for (_ <- 0 to (stashCapacity * 2)) {
        es.tell(DurableStateStringList.DoNothing(probe.ref))
      }
      // capacity + 1 should mean that we get a dropped last message when all stash is filled
      // while the actor is stuck in replay because journal isn't responding
      droppedMessageProbe.receiveMessage()
      implicit val classicSystem: pekko.actor.ActorSystem =
        testKit.system.toClassic
      // we only need to do this one step and recovery completes
      SteppingInmemJournal.step(journal)

      // exactly how many is racy but at least the first stash buffer full should complete
      probe.receiveMessages(stashCapacity)
    }

  }

}

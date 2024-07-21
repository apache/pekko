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

package org.apache.pekko.persistence.typed.scaladsl

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

// Reproducer for #29401
object EventSourcedStashOverflowSpec {

  object EventSourcedStringList {
    sealed trait Command
    case class DoNothing(replyTo: ActorRef[Done]) extends Command

    def apply(persistenceId: PersistenceId): Behavior[Command] =
      EventSourcedBehavior[Command, String, List[String]](
        persistenceId,
        Nil,
        (_, command) =>
          command match {
            case DoNothing(replyTo) =>
              Effect.persist(List.empty[String]).thenRun(_ => replyTo ! Done)
          },
        (state, event) =>
          // original reproducer slept 2 seconds here but a pure application of an event seems unlikely to take that long
          // so instead we delay recovery using a special journal
          event :: state
      )
  }

  def conf =
    SteppingInmemJournal.config("EventSourcedStashOverflow").withFallback(ConfigFactory.parseString(s"""
       pekko.persistence {
         typed {
           stash-capacity = 1000 # enough to fail on stack size
           stash-overflow-strategy = "drop"
         }
       }
   """))
}

class EventSourcedStashOverflowSpec
    extends ScalaTestWithActorTestKit(EventSourcedStashOverflowSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import EventSourcedStashOverflowSpec.EventSourcedStringList

  "Stashing in a busy event sourced behavior" must {

    "not cause stack overflow" in {
      val es = spawn(EventSourcedStringList(PersistenceId.ofUniqueId("id-1")))

      // wait for journal to start
      val probe = testKit.createTestProbe[Done]()
      probe.awaitAssert(SteppingInmemJournal.getRef("EventSourcedStashOverflow"), 3.seconds)
      val journal = SteppingInmemJournal.getRef("EventSourcedStashOverflow")

      val droppedMessageProbe = testKit.createDroppedMessageProbe()
      val stashCapacity = testKit.config.getInt("pekko.persistence.typed.stash-capacity")

      for (_ <- 0 to (stashCapacity * 2))
        es.tell(EventSourcedStringList.DoNothing(probe.ref))
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

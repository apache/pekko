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

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.state.DurableStateStoreProvider
import pekko.persistence.state.javadsl.{ DurableStateStore => JDurableStateStore }
import pekko.persistence.state.scaladsl.{ DurableStateStore, DurableStateUpdateStore, GetObjectResult }
import pekko.persistence.typed.PersistenceId

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

object DurableStateBehaviorStashOverflowSpec {

  class TestDurableStateStoreProvider extends DurableStateStoreProvider {
    private val store = new TestDurableStateStorePlugin[Any]

    override def scaladslDurableStateStore(): DurableStateStore[Any] = store

    // Not used here
    override def javadslDurableStateStore(): JDurableStateStore[AnyRef] = null
  }

  object TestDurableStateStorePlugin {
    @volatile var instance: Option[TestDurableStateStorePlugin[_]] = None
    def getInstance(): TestDurableStateStorePlugin[_] = instance.get
  }

  class TestDurableStateStorePlugin[A] extends DurableStateUpdateStore[A] {
    TestDurableStateStorePlugin.instance = Some(this)

    private val promise: Promise[Done] = Promise()

    override def getObject(persistenceId: String): Future[GetObjectResult[A]] =
      Future.successful(GetObjectResult[A](None, 0L))

    override def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): Future[Done] =
      promise.future

    def completeUpsertFuture(): Unit = promise.success(Done)

    override def deleteObject(persistenceId: String): Future[Done] = Future.successful(Done)

    override def deleteObject(persistenceId: String, revision: Long): Future[Done] = Future.successful(Done)
  }

  object DurableStateString {
    sealed trait Command
    case class DoNothing(replyTo: ActorRef[Done]) extends Command
    case class DoPersist(replyTo: ActorRef[Done]) extends Command

    def apply(persistenceId: PersistenceId): Behavior[Command] =
      DurableStateBehavior[Command, String](
        persistenceId,
        "",
        { (_, command) =>
          command match {
            case DoNothing(replyTo) =>
              Effect.reply(replyTo)(Done)

            case DoPersist(replyTo) =>
              Effect.persist("Initial persist").thenRun(_ => replyTo ! Done)
          }
        })
  }

  def conf = ConfigFactory.parseString(s"""
       pekko.persistence {
         state.plugin = "my-state-plugin"
         typed {
           stash-capacity = 20000 # enough to fail on stack size
           stash-overflow-strategy = "drop"
           recurse-when-unstashing-read-only-commands = false
         }
       }
       pekko.jvm-exit-on-fatal-error = off
       my-state-plugin.class = "${classOf[TestDurableStateStoreProvider].getName}"
   """)
}

class DurableStateBehaviorStashOverflowSpec
    extends ScalaTestWithActorTestKit(DurableStateBehaviorStashOverflowSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import DurableStateBehaviorStashOverflowSpec._

  "Stashing in a busy durable state behavior" must {

    "not cause stack overflow" in {
      val es = spawn(DurableStateString(PersistenceId.ofUniqueId("id-1")))

      // wait for journal to start
      val probe = testKit.createTestProbe[Done]()
      val journal = probe.awaitAssert(TestDurableStateStorePlugin.getInstance(), 3.seconds)

      val droppedMessageProbe = testKit.createDroppedMessageProbe()
      val stashCapacity = testKit.config.getInt("pekko.persistence.typed.stash-capacity")

      es.tell(DurableStateString.DoPersist(probe.ref))

      for (_ <- 0 to (stashCapacity * 2)) {
        es.tell(DurableStateString.DoNothing(probe.ref))
      }
      // capacity * 2 should mean that we get many dropped messages when all stash is filled
      // while the actor is stuck in replay because journal isn't responding (checking only one)
      droppedMessageProbe.receiveMessage()
      journal.completeUpsertFuture()

      // exactly how many is racy but at least the first stash buffer full should complete
      probe.receiveMessages(stashCapacity)
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.testkit.scaladsl

import java.io.NotSerializableException

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.testkit.scaladsl.DurableStateBehaviorTestKitSpec.TestCounter
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.state.scaladsl.DurableStateBehavior
import pekko.persistence.typed.state.scaladsl.Effect
import pekko.serialization.DisabledJavaSerializer
import pekko.serialization.jackson.CborSerializable

import org.scalatest.wordspec.AnyWordSpecLike

object DurableStateBehaviorTestKitSpec {

  object TestCounter {
    sealed trait Command
    case object Increment extends Command with CborSerializable
    final case class IncrementWithConfirmation(replyTo: ActorRef[Done]) extends Command with CborSerializable
    final case class GetValue(replyTo: ActorRef[State]) extends Command with CborSerializable
    case object PersistNotSerializableState extends Command with CborSerializable
    case object NotSerializableCommand extends Command
    final case class ReplyNotSerializable(replyTo: ActorRef[NotSerializableReply])
        extends Command
        with CborSerializable

    sealed trait State
    final case class RealState(value: Int) extends State with CborSerializable
    final case class NotSerializableState(value: Int) extends State
    final class NotSerializableReply

    def apply(persistenceId: PersistenceId, emptyState: State = RealState(0)): DurableStateBehavior[Command, State] =
      DurableStateBehavior[Command, State](
        persistenceId,
        emptyState,
        commandHandler = {
          case (RealState(value), Increment) =>
            Effect.persist(RealState(value + 1))
          case (null, Increment) =>
            Effect.persist(RealState(1))
          case (RealState(value), IncrementWithConfirmation(replyTo)) =>
            Effect.persist(RealState(value + 1)).thenReply(replyTo)(_ => Done)
          case (state, GetValue(replyTo)) =>
            Effect.none[TestCounter.State].thenRun(_ => replyTo ! state)
          case (RealState(value), PersistNotSerializableState) =>
            Effect.persist(NotSerializableState(value + 1))
          case (_, NotSerializableCommand) =>
            Effect.none
          case (_, ReplyNotSerializable(replyTo)) =>
            Effect.none[TestCounter.State].thenRun(_ => replyTo ! new NotSerializableReply)
          case (state, command) =>
            throw new IllegalArgumentException(s"Unexpected state [$state] and command [$command]")
        })
  }
}

class DurableStateBehaviorTestKitSpec
    extends ScalaTestWithActorTestKit(DurableStateBehaviorTestKit.config)
    with AnyWordSpecLike
    with LogCapturing {

  private val persistenceId = PersistenceId.ofUniqueId("durable-state-test")

  private def createTestKit(
      emptyState: TestCounter.State = TestCounter.RealState(0),
      settings: DurableStateBehaviorTestKit.SerializationSettings =
        DurableStateBehaviorTestKit.SerializationSettings.enabled) =
    DurableStateBehaviorTestKit[TestCounter.Command, TestCounter.State](
      system,
      TestCounter(persistenceId, emptyState),
      settings)

  "DurableStateBehaviorTestKit" must {

    "run commands and expose the resulting state" in {
      // #basic-test
      val testKit =
        DurableStateBehaviorTestKit[TestCounter.Command, TestCounter.State](
          system,
          TestCounter(PersistenceId.ofUniqueId("durable-state-test")))

      val firstResult = testKit.runCommand(TestCounter.Increment)
      firstResult.command shouldBe TestCounter.Increment
      firstResult.state shouldBe TestCounter.RealState(1)

      val secondResult = testKit.runCommand(TestCounter.Increment)
      secondResult.stateOfType[TestCounter.RealState].value shouldBe 2
      // #basic-test

      intercept[AssertionError] {
        secondResult.stateOfType[TestCounter.NotSerializableState]
      }.getMessage should include("Expected state class")
    }

    "run commands with replies" in {
      val testKit = createTestKit()

      val result = testKit.runCommand[Done](TestCounter.IncrementWithConfirmation(_))
      result.state shouldBe TestCounter.RealState(1)
      result.reply shouldBe Done
      result.replyOfType[Done] shouldBe Done
      result.hasNoReply shouldBe false
    }

    "detect a missing reply" in {
      val testKit = createTestKit()

      intercept[AssertionError] {
        testKit.runCommand[Done](_ => TestCounter.Increment)
      }.getMessage should include("Missing expected reply")
    }

    "expose the current state" in {
      val testKit = createTestKit()

      testKit.getState() shouldBe TestCounter.RealState(0)
      testKit.runCommand(TestCounter.Increment)
      testKit.getState() shouldBe TestCounter.RealState(1)
    }

    "handle a null empty state" in {
      val testKit = createTestKit(emptyState = null)

      testKit.getState() shouldBe null
      testKit.runCommand(TestCounter.Increment).state shouldBe TestCounter.RealState(1)
    }

    "recover state when restarted" in {
      val testKit = createTestKit()

      testKit.runCommand(TestCounter.Increment)
      testKit.runCommand(TestCounter.Increment)

      testKit.restart().state shouldBe TestCounter.RealState(2)
      testKit.getState() shouldBe TestCounter.RealState(2)
    }

    "clear the state and restart" in {
      val testKit = createTestKit()

      testKit.runCommand(TestCounter.Increment)
      testKit.clear()

      testKit.getState() shouldBe TestCounter.RealState(0)
    }

    "start with an empty durable state store" in {
      val firstTestKit =
        DurableStateBehaviorTestKit[TestCounter.Command, TestCounter.State](
          system,
          TestCounter(persistenceId))
      firstTestKit.runCommand(TestCounter.Increment).state shouldBe TestCounter.RealState(1)

      val secondTestKit =
        DurableStateBehaviorTestKit[TestCounter.Command, TestCounter.State](
          system,
          TestCounter(persistenceId))
      secondTestKit.getState() shouldBe TestCounter.RealState(0)
    }

    "detect a non-serializable command" in {
      val testKit = createTestKit()

      val exception = intercept[IllegalArgumentException] {
        testKit.runCommand(TestCounter.NotSerializableCommand)
      }
      exception.getMessage should include("Command")
      exception.getCause.getClass shouldBe classOf[DisabledJavaSerializer.JavaSerializationException]
    }

    "detect a non-serializable state" in {
      val testKit = createTestKit()

      val exception = intercept[IllegalArgumentException] {
        testKit.runCommand(TestCounter.PersistNotSerializableState)
      }
      exception.getMessage should include("State")
      exception.getCause.getClass shouldBe classOf[DisabledJavaSerializer.JavaSerializationException]
    }

    "detect a non-serializable empty state" in {
      val testKit = createTestKit(TestCounter.NotSerializableState(0))

      val exception = intercept[IllegalArgumentException] {
        testKit.runCommand(TestCounter.Increment)
      }
      exception.getMessage should include("Empty State")
      exception.getCause.getClass shouldBe classOf[DisabledJavaSerializer.JavaSerializationException]
    }

    "detect a non-serializable reply" in {
      val testKit = createTestKit()

      val exception = intercept[IllegalArgumentException] {
        testKit.runCommand(TestCounter.ReplyNotSerializable(_))
      }
      exception.getMessage should include("Reply")
      exception.getCause.getClass shouldBe classOf[NotSerializableException]
    }

    "allow serialization verification to be disabled" in {
      val testKit = createTestKit(
        settings = DurableStateBehaviorTestKit.SerializationSettings.disabled)

      testKit.runCommand(TestCounter.NotSerializableCommand).state shouldBe TestCounter.RealState(0)
    }

    "only allow DurableStateBehavior" in {
      intercept[IllegalArgumentException] {
        DurableStateBehaviorTestKit[TestCounter.Command, TestCounter.State](
          system,
          Behaviors.empty[TestCounter.Command])
      }
    }

    "support a catch-all signal handler" in {
      val behavior = TestCounter(persistenceId).receiveSignal {
        case (_, _) => ()
      }

      val testKit =
        DurableStateBehaviorTestKit[TestCounter.Command, TestCounter.State](system, behavior)

      testKit.runCommand(TestCounter.Increment).state shouldBe TestCounter.RealState(1)
    }
  }
}

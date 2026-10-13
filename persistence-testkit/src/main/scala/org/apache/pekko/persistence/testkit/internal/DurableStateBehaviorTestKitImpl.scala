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

package org.apache.pekko.persistence.testkit.internal

import scala.reflect.ClassTag
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.SerializationTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.persistence.testkit.scaladsl.DurableStateBehaviorTestKit
import pekko.persistence.testkit.scaladsl.DurableStateBehaviorTestKit.CommandResult
import pekko.persistence.testkit.scaladsl.DurableStateBehaviorTestKit.CommandResultWithReply
import pekko.persistence.testkit.scaladsl.DurableStateBehaviorTestKit.RestartResult
import pekko.persistence.testkit.scaladsl.DurableStateBehaviorTestKit.SerializationSettings
import pekko.persistence.testkit.state.scaladsl.PersistenceTestKitDurableStateStore
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.state.internal.DurableStateBehaviorImpl
import pekko.persistence.typed.state.internal.DurableStateBehaviorImpl.GetStateReply

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object DurableStateBehaviorTestKitImpl {

  final case class CommandResultImpl[Command, State, Reply](
      command: Command,
      state: State,
      replyOption: Option[Reply])
      extends CommandResultWithReply[Command, State, Reply] {

    override def stateOfType[S <: State: ClassTag]: S =
      ofType(state, "state")

    override def reply: Reply =
      replyOption.getOrElse(throw new AssertionError("No reply"))

    override def replyOfType[R <: Reply: ClassTag]: R =
      ofType(reply, "reply")

    override def hasNoReply: Boolean =
      replyOption.isEmpty

    private def ofType[A: ClassTag](obj: Any, errorParam: String): A =
      obj match {
        case a: A  => a
        case other =>
          val expectedClass = implicitly[ClassTag[A]].runtimeClass
          val actualClass = if (other == null) "null" else other.getClass.getName
          throw new AssertionError(
            s"Expected $errorParam class [${expectedClass.getName}], " +
            s"but was [$actualClass]")
      }
  }

  final case class RestartResultImpl[State](state: State) extends RestartResult[State]
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class DurableStateBehaviorTestKitImpl[Command, State](
    actorTestKit: ActorTestKit,
    behavior: Behavior[Command],
    serializationSettings: SerializationSettings)
    extends DurableStateBehaviorTestKit[Command, State] {

  import DurableStateBehaviorTestKitImpl._

  private def system: ActorSystem[?] = actorTestKit.system

  private val durableStateStore =
    DurableStateStoreRegistry(system.toClassic)
      .durableStateStoreFor[PersistenceTestKitDurableStateStore[Any]](
        PersistenceTestKitDurableStateStore.Identifier)
  durableStateStore.clearAll()

  private val probe = actorTestKit.createTestProbe[Any]()
  private val stateProbe = actorTestKit.createTestProbe[GetStateReply[State]]()
  private var actor: ActorRef[Command] = actorTestKit.spawn(behavior)
  private def internalActor: ActorRef[Any] = actor.unsafeUpcast[Any]
  private val persistenceId = verifyDurableStateBehavior()

  private val serializationTestKit = new SerializationTestKit(system)
  private var emptyStateVerified = false

  override def runCommand(command: Command): CommandResult[Command, State] = {
    preCommandCheck(command)

    actor ! command

    val newState = getState()
    postCommandCheck(newState, reply = None)

    CommandResultImpl[Command, State, Nothing](command, newState, None)
  }

  override def runCommand[R](creator: ActorRef[R] => Command): CommandResultWithReply[Command, State, R] = {
    val replyProbe = actorTestKit.createTestProbe[R]()
    val command = creator(replyProbe.ref)
    preCommandCheck(command)

    actor ! command

    val reply =
      try {
        replyProbe.receiveMessage()
      } catch {
        case NonFatal(_) =>
          throw new AssertionError(s"Missing expected reply for command [$command].")
      } finally {
        replyProbe.stop()
      }

    val newState = getState()
    postCommandCheck(newState, Some(reply))

    CommandResultImpl(command, newState, Some(reply))
  }

  override def getState(): State = {
    internalActor ! DurableStateBehaviorImpl.GetStateWithReply(stateProbe.ref)
    stateProbe.receiveMessage().currentState
  }

  override def restart(): RestartResult[State] = {
    actorTestKit.stop(actor)
    actor = actorTestKit.spawn(behavior)
    try {
      RestartResultImpl(getState())
    } catch {
      case NonFatal(_) =>
        throw new IllegalStateException("Could not restart DurableStateBehavior. See logs.")
    }
  }

  override def clear(): Unit = {
    durableStateStore.clearByPersistenceId(persistenceId.id)
    restart()
  }

  private def verifyDurableStateBehavior(): PersistenceId = {
    internalActor ! DurableStateBehaviorImpl.GetPersistenceIdForTestKit(probe.ref)
    try {
      probe.expectMessageType[PersistenceId]
    } catch {
      case NonFatal(_) =>
        throw new IllegalArgumentException("Only DurableStateBehavior, or nested DurableStateBehavior allowed.")
    }
  }

  private def preCommandCheck(command: Command): Unit = {
    if (serializationSettings.enabled) {
      if (serializationSettings.verifyCommands)
        verifySerializationAndThrow(command, "Command")

      if (serializationSettings.verifyState && !emptyStateVerified) {
        val emptyState = getState()
        verifySerializationAndThrow(emptyState, "Empty State")
        emptyStateVerified = true
      }
    }
  }

  private def postCommandCheck(newState: State, reply: Option[Any]): Unit = {
    if (serializationSettings.enabled) {
      if (serializationSettings.verifyState)
        verifySerializationAndThrow(newState, "State")

      if (serializationSettings.verifyCommands)
        reply.foreach(verifySerializationAndThrow(_, "Reply"))
    }
  }

  private def verifySerializationAndThrow(obj: Any, errorMessagePrefix: String): Unit = {
    try {
      serializationTestKit.verifySerialization(obj, serializationSettings.verifyEquality)
    } catch {
      case NonFatal(exc) =>
        throw new IllegalArgumentException(s"$errorMessagePrefix [$obj] isn't serializable.", exc)
    }
  }
}

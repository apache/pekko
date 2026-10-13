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

package org.apache.pekko.persistence.testkit.javadsl

import java.util.function.{ Function => JFunction }

import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.annotation.ApiMayChange
import pekko.annotation.DoNotInherit
import pekko.persistence.testkit.scaladsl

import com.typesafe.config.Config

/**
 * Testing of [[pekko.persistence.typed.state.javadsl.DurableStateBehavior]] implementations.
 * It supports running one command at a time and asserting that the synchronously returned result is as expected.
 * The result contains the new state after the command has been processed.
 * It also has support for verifying the reply to a command.
 *
 * Serialization of commands and state is verified automatically.
 *
 * @since 2.0.0
 */
@ApiMayChange
object DurableStateBehaviorTestKit {

  /**
   * The configuration to be included in the configuration of the `ActorSystem`. Typically used as
   * constructor parameter to `TestKitJunitResource`. The configuration enables the in-memory
   * durable state store.
   */
  val config: Config = scaladsl.DurableStateBehaviorTestKit.config

  val enabledSerializationSettings: SerializationSettings = new SerializationSettings(
    enabled = true,
    verifyEquality = false,
    verifyCommands = true,
    verifyState = true)

  val disabledSerializationSettings: SerializationSettings = new SerializationSettings(
    enabled = false,
    verifyEquality = false,
    verifyCommands = false,
    verifyState = false)

  /**
   * Customization of which serialization checks that are performed.
   * `equals` must be implemented when `verifyEquality` is enabled.
   */
  final class SerializationSettings(
      val enabled: Boolean,
      val verifyEquality: Boolean,
      val verifyCommands: Boolean,
      val verifyState: Boolean) {

    def withEnabled(value: Boolean): SerializationSettings =
      copy(enabled = value)

    def withVerifyEquality(value: Boolean): SerializationSettings =
      copy(verifyEquality = value)

    def withVerifyCommands(value: Boolean): SerializationSettings =
      copy(verifyCommands = value)

    def withVerifyState(value: Boolean): SerializationSettings =
      copy(verifyState = value)

    private def copy(
        enabled: Boolean = this.enabled,
        verifyEquality: Boolean = this.verifyEquality,
        verifyCommands: Boolean = this.verifyCommands,
        verifyState: Boolean = this.verifyState): SerializationSettings =
      new SerializationSettings(enabled, verifyEquality, verifyCommands, verifyState)
  }

  /**
   * Factory method to create a new DurableStateBehaviorTestKit.
   */
  def create[Command, State](
      system: ActorSystem[?],
      behavior: Behavior[Command]): DurableStateBehaviorTestKit[Command, State] =
    create(system, behavior, enabledSerializationSettings)

  /**
   * Factory method to create a new DurableStateBehaviorTestKit with custom [[SerializationSettings]].
   *
   * Note that `equals` must be implemented in the commands and state if `verifyEquality` is enabled.
   */
  def create[Command, State](
      system: ActorSystem[?],
      behavior: Behavior[Command],
      serializationSettings: SerializationSettings): DurableStateBehaviorTestKit[Command, State] = {
    val scaladslSettings = new scaladsl.DurableStateBehaviorTestKit.SerializationSettings(
      enabled = serializationSettings.enabled,
      verifyEquality = serializationSettings.verifyEquality,
      verifyCommands = serializationSettings.verifyCommands,
      verifyState = serializationSettings.verifyState)
    new DurableStateBehaviorTestKit(scaladsl.DurableStateBehaviorTestKit(system, behavior, scaladslSettings))
  }

  /**
   * The result of running a command.
   */
  @DoNotInherit class CommandResult[Command, State](
      delegate: scaladsl.DurableStateBehaviorTestKit.CommandResult[Command, State]) {

    /**
     * The command that was run.
     */
    def command: Command =
      delegate.command

    /**
     * The state after the command has been processed.
     */
    def state: State =
      delegate.state

    /**
     * The state as a given expected type. It will throw `AssertionError` if the state is of a different type.
     */
    def stateOfType[S <: State](stateClass: Class[S]): S = {
      implicit val ct: ClassTag[S] = ClassTag(stateClass)
      delegate.stateOfType[S]
    }
  }

  /**
   * The result of running a command with an `ActorRef<R> replyTo`, i.e. the `runCommand` with a
   * `Function<ActorRef<R>, Command>` parameter.
   */
  final class CommandResultWithReply[Command, State, Reply](
      delegate: scaladsl.DurableStateBehaviorTestKit.CommandResultWithReply[Command, State, Reply])
      extends CommandResult[Command, State](delegate) {

    /**
     * The reply. It will throw `AssertionError` if there was no reply.
     */
    def reply: Reply =
      delegate.reply

    /**
     * The reply as a given expected type. It will throw `AssertionError` if there is no reply or
     * if the reply is of a different type.
     */
    def replyOfType[R <: Reply](replyClass: Class[R]): R = {
      implicit val ct: ClassTag[R] = ClassTag(replyClass)
      delegate.replyOfType[R]
    }

    /**
     * `true` if there is no reply.
     */
    def hasNoReply: Boolean =
      delegate.hasNoReply
  }

  /**
   * The result of restarting the behavior.
   */
  final class RestartResult[State](delegate: scaladsl.DurableStateBehaviorTestKit.RestartResult[State]) {

    /**
     * The state after recovery.
     */
    def state: State =
      delegate.state
  }
}

/**
 * @since 2.0.0
 */
@ApiMayChange
final class DurableStateBehaviorTestKit[Command, State](
    delegate: scaladsl.DurableStateBehaviorTestKit[Command, State]) {

  import DurableStateBehaviorTestKit._

  /**
   * Run one command through the behavior. The returned result contains the state after the command
   * has been processed.
   */
  def runCommand(command: Command): CommandResult[Command, State] =
    new CommandResult(delegate.runCommand(command))

  /**
   * Run one command with a `replyTo: ActorRef` through the behavior. The returned result contains
   * the state after the command has been processed, and the reply.
   */
  def runCommand[R](creator: JFunction[ActorRef[R], Command]): CommandResultWithReply[Command, State, R] =
    new CommandResultWithReply(delegate.runCommand(replyTo => creator.apply(replyTo)))

  /**
   * Retrieve the current state of the behavior.
   */
  def getState(): State =
    delegate.getState()

  /**
   * Restart the behavior, which will then recover from the durable state store. Can be used for testing
   * that the recovery is correct.
   */
  def restart(): RestartResult[State] =
    new RestartResult(delegate.restart())

  /**
   * Clear the state for this behavior from the in-memory durable state store and restart the behavior.
   */
  def clear(): Unit =
    delegate.clear()
}

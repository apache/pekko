/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern

import java.util.Optional

import scala.concurrent.duration.{ Duration, FiniteDuration }

import org.apache.pekko
import pekko.actor.{ ActorRef, DeadLetterSuppression, OneForOneStrategy, Props, SupervisorStrategy }
import pekko.annotation.InternalApi
import pekko.pattern.internal.BackoffOnStopSupervisor
import pekko.util.JavaDurationConverters._

object BackoffSupervisor {

  /**
   * Props for creating a `BackoffSupervisor` actor.
   *
   * Exceptions in the child are handled with the default supervision strategy, i.e.
   * most exceptions will immediately restart the child. You can define another
   * supervision strategy by using [[#propsWithSupervisorStrategy]].
   *
   * @param childProps   the [[pekko.actor.Props]] of the child actor that
   *                     will be started and supervised
   * @param childName    name of the child actor
   * @param minBackoff   minimum (initial) duration until the child actor will
   *                     started again, if it is terminated
   * @param maxBackoff   the exponential back-off is capped to this duration
   * @param randomFactor after calculation of the exponential back-off an additional
   *                     random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                     In order to skip this additional delay pass in `0`.
   */
  @deprecated("Use props with BackoffOpts instead", since = "Akka 2.5.22")
  def props(
      childProps: Props,
      childName: String,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): Props = {
    propsWithSupervisorStrategy(
      childProps,
      childName,
      minBackoff,
      maxBackoff,
      randomFactor,
      SupervisorStrategy.defaultStrategy)
  }

  /**
   * Props for creating a `BackoffSupervisor` actor.
   *
   * Exceptions in the child are handled with the default supervision strategy, i.e.
   * most exceptions will immediately restart the child. You can define another
   * supervision strategy by using [[#propsWithSupervisorStrategy]].
   *
   * @param childProps     the [[pekko.actor.Props]] of the child actor that
   *                       will be started and supervised
   * @param childName      name of the child actor
   * @param minBackoff     minimum (initial) duration until the child actor will
   *                       started again, if it is terminated
   * @param maxBackoff     the exponential back-off is capped to this duration
   * @param randomFactor   after calculation of the exponential back-off an additional
   *                       random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                       In order to skip this additional delay pass in `0`.
   * @param maxNrOfRetries maximum number of attempts to restart the child actor.
   *                       The supervisor will terminate itself after the maxNoOfRetries is reached.
   *                       In order to restart infinitely pass in `-1`.
   */
  @deprecated("Use props with BackoffOpts instead", since = "Akka 2.5.22")
  def props(
      childProps: Props,
      childName: String,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxNrOfRetries: Int): Props = {
    val supervisionStrategy = SupervisorStrategy.defaultStrategy match {
      case oneForOne: OneForOneStrategy => oneForOne.withMaxNrOfRetries(maxNrOfRetries)
      case s                            => s
    }
    propsWithSupervisorStrategy(childProps, childName, minBackoff, maxBackoff, randomFactor, supervisionStrategy)
  }

  /**
   * Props for creating a `BackoffSupervisor` actor.
   *
   * Exceptions in the child are handled with the default supervision strategy, i.e.
   * most exceptions will immediately restart the child. You can define another
   * supervision strategy by using [[#propsWithSupervisorStrategy]].
   *
   * @param childProps   the [[pekko.actor.Props]] of the child actor that
   *                     will be started and supervised
   * @param childName    name of the child actor
   * @param minBackoff   minimum (initial) duration until the child actor will
   *                     started again, if it is terminated
   * @param maxBackoff   the exponential back-off is capped to this duration
   * @param randomFactor after calculation of the exponential back-off an additional
   *                     random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                     In order to skip this additional delay pass in `0`.
   */
  @deprecated("Use props with BackoffOpts instead", since = "Akka 2.5.22")
  def props(
      childProps: Props,
      childName: String,
      minBackoff: java.time.Duration,
      maxBackoff: java.time.Duration,
      randomFactor: Double): Props = {
    props(childProps, childName, minBackoff.asScala, maxBackoff.asScala, randomFactor)
  }

  /**
   * Props for creating a `BackoffSupervisor` actor.
   *
   * Exceptions in the child are handled with the default supervision strategy, i.e.
   * most exceptions will immediately restart the child. You can define another
   * supervision strategy by using [[#propsWithSupervisorStrategy]].
   *
   * @param childProps     the [[pekko.actor.Props]] of the child actor that
   *                       will be started and supervised
   * @param childName      name of the child actor
   * @param minBackoff     minimum (initial) duration until the child actor will
   *                       started again, if it is terminated
   * @param maxBackoff     the exponential back-off is capped to this duration
   * @param randomFactor   after calculation of the exponential back-off an additional
   *                       random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                       In order to skip this additional delay pass in `0`.
   * @param maxNrOfRetries maximum number of attempts to restart the child actor.
   *                       The supervisor will terminate itself after the maxNoOfRetries is reached.
   *                       In order to restart infinitely pass in `-1`.
   */
  @deprecated("Use props with BackoffOpts instead", since = "Akka 2.5.22")
  def props(
      childProps: Props,
      childName: String,
      minBackoff: java.time.Duration,
      maxBackoff: java.time.Duration,
      randomFactor: Double,
      maxNrOfRetries: Int): Props = {
    props(childProps, childName, minBackoff.asScala, maxBackoff.asScala, randomFactor, maxNrOfRetries)
  }

  /**
   * Props for creating a `BackoffSupervisor` actor with a custom
   * supervision strategy.
   *
   * Exceptions in the child are handled with the given `supervisionStrategy`. A
   * `Restart` will perform a normal immediate restart of the child. A `Stop` will
   * stop the child, but it will be started again after the back-off duration.
   *
   * @param childProps   the [[pekko.actor.Props]] of the child actor that
   *                     will be started and supervised
   * @param childName    name of the child actor
   * @param minBackoff   minimum (initial) duration until the child actor will
   *                     started again, if it is terminated
   * @param maxBackoff   the exponential back-off is capped to this duration
   * @param randomFactor after calculation of the exponential back-off an additional
   *                     random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                     In order to skip this additional delay pass in `0`.
   * @param strategy     the supervision strategy to use for handling exceptions
   *                     in the child. As the BackoffSupervisor creates a separate actor to handle the
   *                     backoff process, only a [[OneForOneStrategy]] makes sense here.
   */
  @deprecated("Use props with BackoffOpts instead", since = "Akka 2.5.22")
  def propsWithSupervisorStrategy(
      childProps: Props,
      childName: String,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      strategy: SupervisorStrategy): Props = {
    require(minBackoff > Duration.Zero, "minBackoff must be > 0")
    require(maxBackoff >= minBackoff, "maxBackoff must be >= minBackoff")
    require(0.0 <= randomFactor && randomFactor <= 1.0, "randomFactor must be between 0.0 and 1.0")
    Props(
      new BackoffOnStopSupervisor(
        childProps,
        childName,
        minBackoff,
        maxBackoff,
        AutoReset(minBackoff),
        randomFactor,
        strategy,
        ForwardDeathLetters,
        None))
  }

  /**
   * Props for creating a `BackoffSupervisor` actor with a custom
   * supervision strategy.
   *
   * Exceptions in the child are handled with the given `supervisionStrategy`. A
   * `Restart` will perform a normal immediate restart of the child. A `Stop` will
   * stop the child, but it will be started again after the back-off duration.
   *
   * @param childProps   the [[pekko.actor.Props]] of the child actor that
   *                     will be started and supervised
   * @param childName    name of the child actor
   * @param minBackoff   minimum (initial) duration until the child actor will
   *                     started again, if it is terminated
   * @param maxBackoff   the exponential back-off is capped to this duration
   * @param randomFactor after calculation of the exponential back-off an additional
   *                     random delay based on this factor is added, e.g. `0.2` adds up to `20%` delay.
   *                     In order to skip this additional delay pass in `0`.
   * @param strategy     the supervision strategy to use for handling exceptions
   *                     in the child. As the BackoffSupervisor creates a separate actor to handle the
   *                     backoff process, only a [[OneForOneStrategy]] makes sense here.
   */
  @deprecated("Use props with BackoffOpts instead", since = "Akka 2.5.22")
  def propsWithSupervisorStrategy(
      childProps: Props,
      childName: String,
      minBackoff: java.time.Duration,
      maxBackoff: java.time.Duration,
      randomFactor: Double,
      strategy: SupervisorStrategy): Props = {
    propsWithSupervisorStrategy(childProps, childName, minBackoff.asScala, maxBackoff.asScala, randomFactor, strategy)
  }

  /**
   * Props for creating a `BackoffSupervisor` actor from [[BackoffOptions]].
   *
   * @param options the [[BackoffOptions]] that specify how to construct a backoff-supervisor.
   */
  @deprecated("Use new API from BackoffOpts object instead", since = "Akka 2.5.22")
  def props(options: BackoffOptions): Props = options.props

  /**
   * Props for creating a `BackoffSupervisor` actor from [[BackoffOnStopOptions]].
   *
   * @param options the [[BackoffOnStopOptions]] that specify how to construct a backoff-supervisor.
   */
  def props(options: BackoffOnStopOptions): Props = options.props

  /**
   * Props for creating a `BackoffSupervisor` actor from [[BackoffOnFailureOptions]].
   *
   * @param options the [[BackoffOnFailureOptions]] that specify how to construct a backoff-supervisor.
   */
  def props(options: BackoffOnFailureOptions): Props = options.props

  /**
   * Send this message to the `BackoffSupervisor` and it will reply with
   * [[BackoffSupervisor.CurrentChild]] containing the `ActorRef` of the current child, if any.
   */
  case object GetCurrentChild

  /**
   * Java API: Send this message to the `BackoffSupervisor` and it will reply with
   * [[BackoffSupervisor.CurrentChild]] containing the `ActorRef` of the current child, if any.
   */
  def getCurrentChild = GetCurrentChild

  /**
   * Send this message to the `BackoffSupervisor` and it will reply with
   * [[BackoffSupervisor.CurrentChild]] containing the `ActorRef` of the current child, if any.
   */
  final case class CurrentChild(ref: Option[ActorRef]) {

    /**
     * Java API: The `ActorRef` of the current child, if any
     */
    def getRef: Optional[ActorRef] = Optional.ofNullable(ref.orNull)
  }

  /**
   * Send this message to the `BackoffSupervisor` and it will reset the back-off.
   * This should be used in conjunction with `withManualReset` in [[BackoffOptions]].
   */
  case object Reset

  /**
   * Java API: Send this message to the `BackoffSupervisor` and it will reset the back-off.
   * This should be used in conjunction with `withManualReset` in [[BackoffOptions]].
   */
  def reset = Reset

  /**
   * Send this message to the `BackoffSupervisor` and it will reply with
   * [[BackoffSupervisor.RestartCount]] containing the current restart count.
   */
  case object GetRestartCount

  /**
   * Java API: Send this message to the `BackoffSupervisor` and it will reply with
   * [[BackoffSupervisor.RestartCount]] containing the current restart count.
   */
  def getRestartCount = GetRestartCount

  final case class RestartCount(count: Int)

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] case object StartChild extends DeadLetterSuppression

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] case class ResetRestartCount(current: Int) extends DeadLetterSuppression
}

final class BackoffSupervisor @deprecated("Use `BackoffSupervisor.props` method instead", since = "Akka 2.5.22") (
    override val childProps: Props,
    override val childName: String,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    override val reset: BackoffReset,
    randomFactor: Double,
    strategy: SupervisorStrategy,
    val replyWhileStopped: Option[Any],
    val finalStopMessage: Option[Any => Boolean])
    extends BackoffOnStopSupervisor(
      childProps,
      childName,
      minBackoff,
      maxBackoff,
      reset,
      randomFactor,
      strategy,
      replyWhileStopped.map(msg => ReplyWith(msg)).getOrElse(ForwardDeathLetters),
      finalStopMessage) {

  // for binary compatibility with 2.5.18
  @deprecated("Use `BackoffSupervisor.props` method instead", since = "Akka 2.5.22")
  def this(
      childProps: Props,
      childName: String,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      reset: BackoffReset,
      randomFactor: Double,
      strategy: SupervisorStrategy,
      replyWhileStopped: Option[Any]) =
    this(childProps, childName, minBackoff, maxBackoff, reset, randomFactor, strategy, replyWhileStopped, None)

  // for binary compatibility with 2.4.1
  @deprecated("Use `BackoffSupervisor.props` method instead", since = "Akka 2.5.22")
  def this(
      childProps: Props,
      childName: String,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      supervisorStrategy: SupervisorStrategy) =
    this(
      childProps,
      childName,
      minBackoff,
      maxBackoff,
      AutoReset(minBackoff),
      randomFactor,
      supervisorStrategy,
      None,
      None)

  // for binary compatibility with 2.4.0
  @deprecated("Use `BackoffSupervisor.props` method instead", since = "Akka 2.5.22")
  def this(
      childProps: Props,
      childName: String,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double) =
    this(childProps, childName, minBackoff, maxBackoff, randomFactor, SupervisorStrategy.defaultStrategy)
}

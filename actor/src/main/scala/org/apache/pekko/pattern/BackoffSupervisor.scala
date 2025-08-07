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

import org.apache.pekko
import pekko.actor.{ ActorRef, DeadLetterSuppression, Props }
import pekko.annotation.InternalApi

object BackoffSupervisor {

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

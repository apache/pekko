/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern.internal

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, OneForOneStrategy, Props, SupervisorStrategy, Terminated }
import pekko.actor.SupervisorStrategy.{ Directive, Escalate }
import pekko.annotation.InternalApi
import pekko.pattern.{
  BackoffReset,
  BackoffSupervisor,
  ForwardDeathLetters,
  ForwardTo,
  HandleBackoff,
  HandlingWhileStopped,
  ReplyWith,
  RetrySupport
}

/**
 * INTERNAL API
 *
 * Back-off supervisor that stops and starts a child actor using a back-off algorithm when the child actor stops.
 * This back-off supervisor is created by using `org.apache.pekko.pattern.BackoffSupervisor.props`
 * with `BackoffOpts.onStop`.
 */
@InternalApi private[pattern] class BackoffOnStopSupervisor(
    val childProps: Props,
    val childName: String,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    val reset: BackoffReset,
    randomFactor: Double,
    strategy: SupervisorStrategy,
    handlingWhileStopped: HandlingWhileStopped,
    finalStopMessage: Option[Any => Boolean])
    extends Actor
    with HandleBackoff
    with ActorLogging {

  import BackoffSupervisor._
  import context.dispatcher

  override val supervisorStrategy: SupervisorStrategy = {
    val decider = super.supervisorStrategy.decider
    strategy match {
      case oneForOne: OneForOneStrategy =>
        OneForOneStrategy(oneForOne.maxNrOfRetries, oneForOne.withinTimeRange, oneForOne.loggingEnabled) {
          case ex =>
            val defaultDirective: Directive =
              decider.applyOrElse(ex, (_: Any) => Escalate)

            strategy.decider.applyOrElse(ex, (_: Any) => defaultDirective)
        }
      case s => s
    }
  }

  def onTerminated: Receive = {
    case Terminated(ref) if child.contains(ref) =>
      child = None
      if (finalStopMessageReceived) {
        context.stop(self)
      } else {
        val maxNrOfRetries = strategy match {
          case oneForOne: OneForOneStrategy => oneForOne.maxNrOfRetries
          case _                            => -1
        }
        val nextRestartCount = restartCount + 1

        if (maxNrOfRetries == -1 || nextRestartCount <= maxNrOfRetries) {
          val restartDelay = RetrySupport.calculateDelay(restartCount, minBackoff, maxBackoff, randomFactor)
          context.system.scheduler.scheduleOnce(restartDelay, self, StartChild)
          restartCount = nextRestartCount
        } else {
          log.debug(
            s"Terminating on restart #{} which exceeds max allowed restarts ({})",
            nextRestartCount,
            maxNrOfRetries)
          context.stop(self)
        }
      }

  }

  def receive: Receive = onTerminated.orElse(handleBackoff)

  protected def handleMessageToChild(msg: Any): Unit = child match {
    case Some(c) =>
      c.forward(msg)
      if (!finalStopMessageReceived) finalStopMessage match {
        case Some(fsm) => finalStopMessageReceived = fsm(msg)
        case None      =>
      }
    case None =>
      finalStopMessage match {
        case Some(fsm) if fsm(msg) => context.stop(self)
        case _                     =>
          handlingWhileStopped match {
            case ForwardDeathLetters => context.system.deadLetters.forward(msg)
            case ForwardTo(h)        => h.forward(msg)
            case ReplyWith(r)        => sender() ! r
          }
      }
  }
}

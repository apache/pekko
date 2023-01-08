/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern.internal

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ OneForOneStrategy, _ }
import pekko.actor.SupervisorStrategy._
import pekko.annotation.InternalApi
import pekko.pattern.{
  BackoffReset,
  BackoffSupervisor,
  ForwardDeathLetters,
  ForwardTo,
  HandleBackoff,
  HandlingWhileStopped,
  ReplyWith
}

/**
 * INTERNAL API
 *
 * Back-off supervisor that stops and starts a child actor when the child actor restarts.
 * This back-off supervisor is created by using ``org.apache.pekko.pattern.BackoffSupervisor.props``
 * with ``org.apache.pekko.pattern.BackoffOpts.onFailure``.
 */
@InternalApi private[pattern] class BackoffOnRestartSupervisor(
    val childProps: Props,
    val childName: String,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    val reset: BackoffReset,
    randomFactor: Double,
    strategy: OneForOneStrategy,
    handlingWhileStopped: HandlingWhileStopped)
    extends Actor
    with HandleBackoff
    with ActorLogging {

  import BackoffSupervisor._
  import context._

  override val supervisorStrategy: OneForOneStrategy = {
    val decider = super.supervisorStrategy.decider
    OneForOneStrategy(strategy.maxNrOfRetries, strategy.withinTimeRange, strategy.loggingEnabled) {
      case ex =>
        val defaultDirective: Directive =
          decider.applyOrElse(ex, (_: Any) => Escalate)

        strategy.decider.applyOrElse(ex, (_: Any) => defaultDirective) match {
          // Whatever the final Directive is, we will translate all Restarts
          // to our own Restarts, which involves stopping the child.
          case Restart =>
            val nextRestartCount = restartCount + 1

            if (strategy.withinTimeRange.isFinite && restartCount == 0) {
              // If the user has defined a time range for the maxNrOfRetries, we'll schedule a message
              // to ourselves every time that range elapses, to reset the restart counter. We hide it
              // behind this conditional to avoid queuing the message unnecessarily
              val finiteWithinTimeRange = strategy.withinTimeRange.asInstanceOf[FiniteDuration]
              system.scheduler.scheduleOnce(finiteWithinTimeRange, self, ResetRestartCount(nextRestartCount))
            }
            val childRef = sender()
            if (strategy.maxNrOfRetries >= 0 && nextRestartCount > strategy.maxNrOfRetries) {
              // If we've exceeded the maximum # of retries allowed by the Strategy, die.
              log.debug(
                s"Terminating on restart #{} which exceeds max allowed restarts ({})",
                nextRestartCount,
                strategy.maxNrOfRetries)
              become(receive)
              stop(self)
            } else {
              become(waitChildTerminatedBeforeBackoff(childRef).orElse(handleBackoff))
            }
            Stop

          case other => other
        }
    }
  }

  def waitChildTerminatedBeforeBackoff(childRef: ActorRef): Receive = {
    case Terminated(`childRef`) =>
      become(receive)
      child = None
      val restartDelay = BackoffSupervisor.calculateDelay(restartCount, minBackoff, maxBackoff, randomFactor)
      context.system.scheduler.scheduleOnce(restartDelay, self, BackoffSupervisor.StartChild)
      restartCount += 1

    case StartChild => // Ignore it, we will schedule a new one once current child terminated.
  }

  def onTerminated: Receive = {
    case Terminated(c) =>
      log.debug(s"Terminating, because child [$c] terminated itself")
      stop(self)
  }

  def receive: Receive = onTerminated.orElse(handleBackoff)

  protected def handleMessageToChild(msg: Any): Unit = child match {
    case Some(c) =>
      c.forward(msg)
    case None =>
      handlingWhileStopped match {
        case ForwardDeathLetters => context.system.deadLetters.forward(msg)
        case ForwardTo(h)        => h.forward(msg)
        case ReplyWith(r)        => sender() ! r
      }
  }
}

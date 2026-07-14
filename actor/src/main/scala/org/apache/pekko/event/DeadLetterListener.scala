/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.event

import scala.concurrent.duration.Deadline
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko

import pekko.actor.Actor
import pekko.actor.ActorLogMarker
import pekko.actor.ActorRef
import pekko.actor.AllDeadLetters
import pekko.actor.CoordinatedShutdown
import pekko.actor.DeadLetter
import pekko.actor.DeadLetterActorRef
import pekko.actor.DeadLetterSuppression
import pekko.actor.Dropped
import pekko.actor.UnhandledMessage
import pekko.actor.WrappedMessage
import pekko.event.Logging.Info
import pekko.util.PrettyDuration._

class DeadLetterListener extends Actor {

  val eventStream: EventStream = context.system.eventStream
  protected val maxCount: Int = context.system.settings.LogDeadLetters
  private val isAlwaysLoggingDeadLetters = maxCount == Int.MaxValue
  // When dead-letter logging during shutdown is disabled we must stop logging as soon as the
  // system starts terminating, not only once this listener is finally stopped at the very end of
  // CoordinatedShutdown. Otherwise dead letters produced throughout the (potentially long) shutdown
  // are still logged unconditionally. See issue #3256.
  private val logDeadLettersDuringShutdown = context.system.settings.LogDeadLettersDuringShutdown
  // The `pekko.coordinated-shutdown` config, used to resolve the EFFECTIVE (per-reason)
  // `terminate-actor-system` value of an in-progress shutdown; see `terminatingShutdownInProgress`.
  private val coordinatedShutdownConfig = context.system.settings.config.getConfig("pekko.coordinated-shutdown")
  // Resolved lazily so that handling a dead letter never forces CoordinatedShutdown initialization
  // from the actor's constructor. At worst the first dead letter triggers the one-time extension
  // initialization; afterwards this just caches the reference and avoids a per-dead-letter lookup.
  private lazy val coordinatedShutdown = CoordinatedShutdown(context.system)
  // Cached decision of whether the in-progress coordinated shutdown actually terminates the system.
  // The shutdown reason is set once and never changes, so this is computed at most once. Accessed
  // only from the actor's own thread, so a plain var is sufficient.
  private var shutdownTerminatesSystem: Option[Boolean] = None
  protected var count: Int = 0

  override def preStart(): Unit = {
    eventStream.subscribe(self, classOf[DeadLetter])
    eventStream.subscribe(self, classOf[Dropped])
    eventStream.subscribe(self, classOf[UnhandledMessage])
  }

  // don't re-subscribe, skip call to preStart
  override def postRestart(reason: Throwable): Unit = ()

  // don't remove subscription, skip call to postStop, no children to stop
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = ()

  override def postStop(): Unit =
    eventStream.unsubscribe(self)

  private def incrementCount(): Unit = {
    // `count` is public API (for unknown reason) so for backwards compatibility reasons we
    // can't change it to Long
    if (count == Int.MaxValue) {
      Logging.getLogger(this).info("Resetting DeadLetterListener counter after reaching Int.MaxValue.")
      count = 1
    } else
      count += 1
  }

  def receive: Receive =
    if (isAlwaysLoggingDeadLetters) receiveWithAlwaysLogging
    else
      context.system.settings.LogDeadLettersSuspendDuration match {
        case suspendDuration: FiniteDuration => receiveWithSuspendLogging(suspendDuration)
        case _                               => receiveWithMaxCountLogging
      }

  private def receiveWithAlwaysLogging: Receive = {
    case d: AllDeadLetters =>
      if (!isSuppressed(d)) {
        incrementCount()
        logDeadLetter(d, doneMsg = "")
      }
  }

  private def receiveWithMaxCountLogging: Receive = {
    case d: AllDeadLetters =>
      if (!isSuppressed(d)) {
        incrementCount()
        if (count == maxCount) {
          logDeadLetter(d, ", no more dead letters will be logged")
          context.stop(self)
        } else {
          logDeadLetter(d, "")
        }
      }
  }

  private def receiveWithSuspendLogging(suspendDuration: FiniteDuration): Receive = {
    case d: AllDeadLetters =>
      if (!isSuppressed(d)) {
        incrementCount()
        if (count == maxCount) {
          val doneMsg = s", no more dead letters will be logged in next [${suspendDuration.pretty}]"
          logDeadLetter(d, doneMsg)
          context.become(receiveWhenSuspended(suspendDuration, Deadline.now + suspendDuration))
        } else
          logDeadLetter(d, "")
      }
  }

  private def receiveWhenSuspended(suspendDuration: FiniteDuration, suspendDeadline: Deadline): Receive = {
    case d: AllDeadLetters =>
      if (!isSuppressed(d)) {
        incrementCount()
        if (suspendDeadline.isOverdue()) {
          val doneMsg = s", of which ${count - maxCount - 1} were not logged. The counter will be reset now"
          logDeadLetter(d, doneMsg)
          count = 0
          context.become(receiveWithSuspendLogging(suspendDuration))
        }
      }
  }

  private def logDeadLetter(d: AllDeadLetters, doneMsg: String): Unit = {
    val origin = if (isReal(d.sender)) s" from ${d.sender}" else ""
    val unwrapped = WrappedMessage.unwrap(d.message)
    val messageStr = unwrapped.getClass.getName
    val wrappedIn = if (d.message.isInstanceOf[WrappedMessage]) s" wrapped in [${d.message.getClass.getName}]" else ""
    val logMessage = d match {
      case dropped: Dropped =>
        val destination = if (isReal(d.recipient)) s" to ${d.recipient}" else ""
        s"Message [$messageStr]$wrappedIn$origin$destination was dropped. ${dropped.reason}. " +
        s"[$count] dead letters encountered$doneMsg. "
      case _: UnhandledMessage =>
        val destination = if (isReal(d.recipient)) s" to ${d.recipient}" else ""
        s"Message [$messageStr]$wrappedIn$origin$destination was unhandled. " +
        s"[$count] dead letters encountered$doneMsg. "
      case _ =>
        s"Message [$messageStr]$wrappedIn$origin to ${d.recipient} was not delivered. " +
        s"[$count] dead letters encountered$doneMsg. " +
        s"If this is not an expected behavior then ${d.recipient} may have terminated unexpectedly. "
    }
    eventStream.publish(
      Info(
        d.recipient.path.toString,
        d.recipient.getClass,
        logMessage +
        "This logging can be turned off or adjusted with configuration settings 'pekko.log-dead-letters' " +
        "and 'pekko.log-dead-letters-during-shutdown'.",
        Logging.emptyMDC,
        ActorLogMarker.deadLetter(messageStr)))
  }

  private def isReal(snd: ActorRef): Boolean = {
    (snd ne ActorRef.noSender) && (snd ne context.system.deadLetters) && !snd.isInstanceOf[DeadLetterActorRef]
  }

  private def isSuppressed(d: AllDeadLetters): Boolean =
    isWrappedSuppressed(d) || suppressedDuringShutdown

  // Suppress logging while a terminating coordinated shutdown is in progress and dead-letter logging
  // during shutdown is disabled (`pekko.log-dead-letters-during-shutdown`, `off` by default).
  private def suppressedDuringShutdown: Boolean =
    !logDeadLettersDuringShutdown && terminatingShutdownInProgress

  // True while a coordinated shutdown that actually terminates this ActorSystem is in progress.
  // `terminate-actor-system` can be overridden per shutdown reason (CoordinatedShutdown
  // reason-overrides), so the decision must be taken from the EFFECTIVE config for the active reason
  // rather than from the base setting: a non-terminating run leaves a shutdown reason set on a
  // still-running system and must NOT suppress logging, otherwise dead-letter logging would be
  // silently disabled for the rest of that system's life (issue #3256). `confWithOverrides` is the
  // same resolution CoordinatedShutdown itself uses to decide whether to terminate the system.
  private def terminatingShutdownInProgress: Boolean =
    coordinatedShutdown.shutdownReason() match {
      case reason @ Some(_) =>
        shutdownTerminatesSystem.getOrElse {
          val terminates =
            CoordinatedShutdown.confWithOverrides(coordinatedShutdownConfig, reason).getBoolean(
              "terminate-actor-system")
          shutdownTerminatesSystem = Some(terminates)
          terminates
        }
      case None => false
    }

  private def isWrappedSuppressed(d: AllDeadLetters): Boolean = {
    d.message match {
      case w: WrappedMessage if w.message.isInstanceOf[DeadLetterSuppression] => true
      case _                                                                  => false
    }
  }

}

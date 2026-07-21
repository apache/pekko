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

package org.apache.pekko.actor.dungeon

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorCell
import pekko.actor.Cancellable
import pekko.actor.dungeon.ReceiveTimeoutCompat

private[pekko] object ReceiveTimeout {
  // Compared only across one message invocation, so wrapping over an actor's lifetime is harmless.
  private final class State(var timeout: Duration, var task: Cancellable, var version: Int)
}

private[pekko] trait ReceiveTimeout { this: ActorCell =>

  import ActorCell._
  import ReceiveTimeout._

  private var receiveTimeoutData: State = null

  final def receiveTimeout: Duration =
    if (receiveTimeoutData eq null) Duration.Undefined else receiveTimeoutData.timeout

  final def setReceiveTimeout(timeout: Duration): Unit = {
    val data = receiveTimeoutData
    if (data eq null)
      receiveTimeoutData = new State(timeout, emptyCancellable, version = 1)
    else {
      data.timeout = timeout
      data.version += 1
    }
  }

  /** Called after `ActorCell.receiveMessage` or `ActorCell.autoReceiveMessage`. */
  protected def checkReceiveTimeoutIfNeeded(message: Any, beforeReceiveVersion: Int): Unit = {
    val timeoutChanged = receiveTimeoutChanged(beforeReceiveVersion)
    if (hasTimeoutData || timeoutChanged)
      checkReceiveTimeout(!ReceiveTimeoutCompat.isNotInfluenceReceiveTimeout(message) || timeoutChanged)
  }

  final def checkReceiveTimeout(reschedule: Boolean): Unit = {
    val data = receiveTimeoutData
    if (data ne null) {
      data.timeout match {
        case f: FiniteDuration =>
          // The fact that timeout is FiniteDuration and task is emptyCancellable
          // means that a user called `context.setReceiveTimeout(...)`
          // while sending the ReceiveTimeout message is not scheduled yet.
          // We have to handle the case and schedule sending the ReceiveTimeout message
          // ignoring the reschedule parameter.
          if (reschedule || (data.task eq emptyCancellable))
            rescheduleReceiveTimeout(data, f)

        case _ => cancelReceiveTimeoutTask()
      }
    }
  }

  private def rescheduleReceiveTimeout(data: State, timeout: FiniteDuration): Unit = {
    data.task.cancel() // Cancel any ongoing future
    data.task = system.scheduler.scheduleOnce(timeout, self, pekko.actor.ReceiveTimeout)(this.dispatcher)
    data.version += 1
  }

  private def hasTimeoutData: Boolean = receiveTimeoutData ne null

  private def receiveTimeoutVersion: Int =
    if (receiveTimeoutData eq null) 0 else receiveTimeoutData.version

  private def receiveTimeoutChanged(beforeReceiveVersion: Int): Boolean =
    receiveTimeoutVersion != beforeReceiveVersion

  protected def cancelReceiveTimeoutIfNeeded(message: Any): Int = {
    if (hasTimeoutData && !ReceiveTimeoutCompat.isNotInfluenceReceiveTimeout(message))
      cancelReceiveTimeoutTask()

    receiveTimeoutVersion
  }

  private[pekko] def cancelReceiveTimeoutTask(): Unit = {
    val data = receiveTimeoutData
    if ((data ne null) && (data.task ne emptyCancellable)) {
      data.task.cancel()
      data.task = emptyCancellable
      data.version += 1
    }
  }

}

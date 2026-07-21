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
  final val emptyReceiveTimeoutData: (Duration, Cancellable) = (Duration.Undefined, ActorCell.emptyCancellable)
}

private[pekko] trait ReceiveTimeout { this: ActorCell =>

  import ActorCell._
  import ReceiveTimeout._

  private var receiveTimeoutData: (Duration, Cancellable) = emptyReceiveTimeoutData

  final def receiveTimeout: Duration = receiveTimeoutData._1

  final def setReceiveTimeout(timeout: Duration): Unit = receiveTimeoutData = receiveTimeoutData.copy(_1 = timeout)

  /** Called after `ActorCell.receiveMessage` or `ActorCell.autoReceiveMessage`. */
  protected def checkReceiveTimeoutIfNeeded(beforeReceive: (Duration, Cancellable)): Unit = {
    val timeoutChanged = receiveTimeoutChanged(beforeReceive)
    if (hasTimeoutData || timeoutChanged)
      checkReceiveTimeout(timeoutChanged)
  }

  final def checkReceiveTimeout(reschedule: Boolean): Unit = {
    val (recvTimeout, task) = receiveTimeoutData
    recvTimeout match {
      case f: FiniteDuration =>
        // The fact that recvTimeout is FiniteDuration and task is emptyCancellable
        // means that a user called `context.setReceiveTimeout(...)`
        // while sending the ReceiveTimeout message is not scheduled yet.
        // We have to handle the case and schedule sending the ReceiveTimeout message
        // ignoring the reschedule parameter.
        if (reschedule || (task eq emptyCancellable))
          rescheduleReceiveTimeout(f)

      case _ => cancelReceiveTimeoutTask()
    }
  }

  private def rescheduleReceiveTimeout(f: FiniteDuration): Unit = {
    receiveTimeoutData._2.cancel() // Cancel any ongoing future
    val task = system.scheduler.scheduleOnce(f, self, pekko.actor.ReceiveTimeout)(this.dispatcher)
    receiveTimeoutData = (f, task)
  }

  private def hasTimeoutData: Boolean = receiveTimeoutData ne emptyReceiveTimeoutData

  private def receiveTimeoutChanged(beforeReceive: (Duration, Cancellable)): Boolean =
    receiveTimeoutData ne beforeReceive

  protected def cancelReceiveTimeoutIfNeeded(message: Any): (Duration, Cancellable) = {
    val beforeReceive = receiveTimeoutData
    if ((beforeReceive ne emptyReceiveTimeoutData) && !ReceiveTimeoutCompat.isNotInfluenceReceiveTimeout(message))
      cancelReceiveTimeoutTask()

    // Returning the state from before cancellation lets checkReceiveTimeoutIfNeeded infer whether this message
    // influenced the timeout without performing the type check a second time.
    beforeReceive
  }

  private[pekko] def cancelReceiveTimeoutTask(): Unit =
    if (receiveTimeoutData._2 ne emptyCancellable) {
      receiveTimeoutData._2.cancel()
      receiveTimeoutData = (receiveTimeoutData._1, emptyCancellable)
    }

}

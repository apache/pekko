/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.concurrent.duration.{ Deadline, FiniteDuration }

/**
 * INTERNAL API
 */
private[remote] object RestartCounter {
  final case class State(count: Int, deadline: Deadline)
}

/**
 * INTERNAL API: Thread safe "restarts with duration" counter
 */
private[remote] class RestartCounter(maxRestarts: Int, restartTimeout: FiniteDuration) {
  import RestartCounter._

  private val state = new AtomicReference[State](State(0, Deadline.now + restartTimeout))

  /**
   * Current number of restarts.
   */
  def count(): Int = state.get.count

  /**
   * Increment the restart counter, or reset the counter to 1 if the
   * `restartTimeout` has elapsed. The latter also resets the timeout.
   * @return `true` if number of restarts, including this one, is less
   *         than or equal to `maxRestarts`
   */
  @tailrec final def restart(): Boolean = {
    val s = state.get

    val newState =
      if (s.deadline.hasTimeLeft())
        s.copy(count = s.count + 1)
      else
        State(1, Deadline.now + restartTimeout)

    if (state.compareAndSet(s, newState))
      newState.count <= maxRestarts
    else
      restart() // recur
  }

}

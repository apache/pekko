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

package org.apache.pekko.actor.typed.internal.adapter

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.Cancellable
import pekko.actor.typed.Scheduler
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object SchedulerAdapter {
  def toClassic(scheduler: Scheduler): pekko.actor.Scheduler =
    scheduler match {
      case s: SchedulerAdapter => s.classicScheduler
      case _ =>
        throw new UnsupportedOperationException(
          "unknown Scheduler type " +
          s"($scheduler of class ${scheduler.getClass.getName})")
    }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class SchedulerAdapter(
    private[pekko] val classicScheduler: pekko.actor.Scheduler) extends Scheduler {
  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(
      implicit executor: ExecutionContext): Cancellable =
    classicScheduler.scheduleOnce(delay, runnable)

  override def scheduleOnce(delay: Duration, runnable: Runnable, executor: ExecutionContext): Cancellable =
    classicScheduler.scheduleOnce(delay, runnable)(executor)

  override def scheduleWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration)(runnable: Runnable)(
      implicit executor: ExecutionContext): Cancellable =
    classicScheduler.scheduleWithFixedDelay(initialDelay, delay)(runnable)

  override def scheduleWithFixedDelay(
      initialDelay: Duration,
      delay: Duration,
      runnable: Runnable,
      executor: ExecutionContext): Cancellable =
    classicScheduler.scheduleWithFixedDelay(initialDelay, delay, runnable, executor)

  override def scheduleAtFixedRate(initialDelay: FiniteDuration, interval: FiniteDuration)(runnable: Runnable)(
      implicit executor: ExecutionContext): Cancellable =
    classicScheduler.scheduleAtFixedRate(initialDelay, interval)(runnable)

  override def scheduleAtFixedRate(
      initialDelay: Duration,
      interval: Duration,
      runnable: Runnable,
      executor: ExecutionContext): Cancellable =
    classicScheduler.scheduleAtFixedRate(initialDelay, interval, runnable, executor)

}

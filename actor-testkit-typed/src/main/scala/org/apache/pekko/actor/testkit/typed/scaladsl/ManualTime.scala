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

package org.apache.pekko.actor.testkit.typed.scaladsl

import scala.annotation.varargs
import scala.concurrent.duration.{ Duration, FiniteDuration }

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.internal.adapter.SchedulerAdapter

/**
 * Manual time allows you to do async tests while controlling the scheduler of the system.
 *
 * To use it you need to configure the `ActorSystem`/`ActorTestKit` with [[ManualTime.config]] and access the
 * scheduler control through [[ManualTime.apply]]
 */
object ManualTime {

  /**
   * Config needed to use the `ExplicitlyTriggeredScheduler`
   */
  val config: Config =
    ConfigFactory.parseString(
      """pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"""")

  /**
   * Access the manual scheduler, note that you need to setup the actor system/testkit with [[ManualTime.config]]
   * for this to work.
   */
  def apply()(implicit system: ActorSystem[_]): ManualTime =
    system.scheduler match {
      case adapter: SchedulerAdapter =>
        adapter.classicScheduler match {
          case sc: pekko.testkit.ExplicitlyTriggeredScheduler => new ManualTime(sc)
          case _                                              =>
            throw new IllegalArgumentException(
              "ActorSystem not configured with explicitly triggered scheduler, " +
              "make sure to include org.apache.pekko.actor.testkit.typed.scaladsl.ManualTime.config() when setting up the test")
        }
      case s =>
        throw new IllegalArgumentException(
          s"ActorSystem.scheduler is not a classic SchedulerAdapter but a ${s.getClass.getName}, this is not supported")
    }

}

/**
 * Not for user instantiation, see [[ManualTime#apply]]
 */
final class ManualTime(delegate: pekko.testkit.ExplicitlyTriggeredScheduler) {

  /**
   * Advance the clock by the specified duration, executing all outstanding jobs on the calling thread before returning.
   *
   * We will not add a dilation factor to this amount, since the scheduler API also does not apply dilation.
   * If you want the amount of time passed to be dilated, apply the dilation before passing the delay to
   * this method.
   */
  def timePasses(amount: FiniteDuration): Unit = delegate.timePasses(amount)

  @varargs
  def expectNoMessageFor(duration: FiniteDuration, on: TestProbe[_]*): Unit = {
    delegate.timePasses(duration)
    on.foreach(_.expectNoMessage(Duration.Zero))
  }

}

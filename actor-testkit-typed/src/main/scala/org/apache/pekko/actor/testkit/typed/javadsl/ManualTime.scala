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

package org.apache.pekko.actor.testkit.typed.javadsl

import java.time.Duration

import scala.annotation.varargs

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.internal.adapter.SchedulerAdapter
import pekko.util.JavaDurationConverters._

/**
 * Manual time allows you to do async tests while controlling the scheduler of the system.
 *
 * To use it you need to configure the `ActorSystem`/`ActorTestKit` with [[ManualTime.config]] and access the
 * scheduler control through [[ManualTime.get]]
 */
object ManualTime {

  /**
   * Config that needs to be in place for the actor system to use the manual
   */
  def config(): Config = pekko.actor.testkit.typed.scaladsl.ManualTime.config

  /**
   * Access the manual scheduler, note that you need to setup the actor system/testkit with [[ManualTime.config]]
   * for this to work.
   */
  def get[A](system: ActorSystem[A]): ManualTime =
    system.scheduler match {
      case adapter: SchedulerAdapter =>
        adapter.classicScheduler match {
          case sc: pekko.testkit.ExplicitlyTriggeredScheduler => new ManualTime(sc)
          case _ =>
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
 * Not for user instantiation, see [[ManualTime#get]]
 */
final class ManualTime(delegate: pekko.testkit.ExplicitlyTriggeredScheduler) {

  /**
   * Advance the clock by the specified duration, executing all outstanding jobs on the calling thread before returning.
   *
   * We will not add a dilation factor to this amount, since the scheduler API also does not apply dilation.
   * If you want the amount of time passed to be dilated, apply the dilation before passing the delay to
   * this method.
   */
  def timePasses(amount: Duration): Unit = delegate.timePasses(amount.asScala)

  @varargs
  def expectNoMessageFor(duration: Duration, on: TestProbe[?]*): Unit = {
    delegate.timePasses(duration.asScala)
    on.foreach(_.expectNoMessage(Duration.ZERO))
  }

}

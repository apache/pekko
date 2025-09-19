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

package org.apache.pekko.util

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration, FiniteDuration }

import language.implicitConversions

@SerialVersionUID(1L)
case class Timeout(duration: FiniteDuration) {

  /**
   * Construct a Timeout from the given time unit and factor.
   */
  def this(length: Long, unit: TimeUnit) = this(Duration(length, unit))
}

/**
 * A Timeout is a wrapper on top of Duration to be more specific about what the duration means.
 */
object Timeout {

  /**
   * A timeout with zero duration, will cause most requests to always timeout.
   */
  val zero: Timeout = new Timeout(Duration.Zero)

  /**
   * Construct a Timeout from the given time unit and factor.
   */
  def apply(length: Long, unit: TimeUnit): Timeout = new Timeout(length, unit)

  /**
   * Create a Timeout from java.time.Duration.
   */
  def create(duration: java.time.Duration): Timeout = {
    import scala.jdk.DurationConverters._
    new Timeout(duration.toScala)
  }

  implicit def durationToTimeout(duration: FiniteDuration): Timeout = new Timeout(duration)
}

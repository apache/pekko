/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import scala.concurrent.duration.FiniteDuration

/**
 * Controlled clock for testing recency windows.
 * Durations are always in seconds.
 */
class TestClock extends Clock {
  private var time = 0L

  def tick(): Unit = time += 1

  override def currentTime(): Long = time

  override def earlierTime(duration: FiniteDuration): Long = currentTime() - duration.toSeconds
}

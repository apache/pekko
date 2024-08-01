/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongUnaryOperator

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi

/**
 * A time source.
 */
@ApiMayChange
trait WallClock {
  def currentTimeMillis(): Long
}

object WallClock {

  /**
   * Always increasing time source. Based on `System.currentTimeMillis()` but
   * guaranteed to always increase for each invocation.
   */
  val AlwaysIncreasingClock: WallClock = new AlwaysIncreasingClock()
}

/**
 * INTERNAL API: Always increasing wall clock time.
 */
@InternalApi
private[pekko] final class AlwaysIncreasingClock() extends AtomicLong with WallClock {

  override def currentTimeMillis(): Long = {
    val currentSystemTime = System.currentTimeMillis()
    updateAndGet {
      new LongUnaryOperator {
        override def applyAsLong(time: Long): Long =
          if (currentSystemTime <= time) time + 1
          else currentSystemTime
      }
    }
  }
}

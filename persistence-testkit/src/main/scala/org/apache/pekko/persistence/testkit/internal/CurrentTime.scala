/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.internal

import java.util.concurrent.atomic.AtomicLong

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object CurrentTime {
  private val previous = new AtomicLong(System.currentTimeMillis())

  /**
   * `System.currentTimeMillis` but always increasing.
   */
  def now(): Long = {
    val current = System.currentTimeMillis()
    val prev = previous.get()
    if (current > prev) {
      previous.compareAndSet(prev, current)
      current
    } else {
      previous.compareAndSet(prev, prev + 1)
      prev + 1
    }
  }
}

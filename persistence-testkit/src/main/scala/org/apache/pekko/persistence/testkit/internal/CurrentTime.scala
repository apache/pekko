/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

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

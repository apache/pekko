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

package org.apache.pekko.testkit

import java.util.concurrent.{ CyclicBarrier, TimeUnit, TimeoutException }

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.actor.ActorSystem

class TestBarrierTimeoutException(message: String) extends RuntimeException(message)

/**
 * A cyclic barrier wrapper for use in testing.
 * It always uses a timeout when waiting and timeouts are specified as durations.
 * Timeouts will always throw an exception. The default timeout is 5 seconds.
 * Timeouts are multiplied by the testing time factor for Jenkins builds.
 */
object TestBarrier {
  val DefaultTimeout = Duration(5, TimeUnit.SECONDS)

  def apply(count: Int) = new TestBarrier(count)
}

class TestBarrier(count: Int) {
  private val barrier = new CyclicBarrier(count)

  def await()(implicit system: ActorSystem): Unit = await(TestBarrier.DefaultTimeout)

  def await(timeout: FiniteDuration)(implicit system: ActorSystem): Unit = {
    try {
      barrier.await(timeout.dilated.toNanos, TimeUnit.NANOSECONDS)
    } catch {
      case _: TimeoutException =>
        throw new TestBarrierTimeoutException(
          "Timeout of %s and time factor of %s".format(timeout.toString, TestKitExtension(system).TestTimeFactor))
    }
  }

  def reset(): Unit = barrier.reset()
}

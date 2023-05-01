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

package org.apache.pekko.pattern

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.Unsafe

import scala.concurrent.duration.FiniteDuration

@InternalApi
private[pekko] trait CircuitBreakerInline { this: CircuitBreaker =>

  /**
   * Helper method for access to underlying state via Unsafe
   *
   * @param oldState Previous state on transition
   * @param newState Next state on transition
   * @return Whether the previous state matched correctly
   */
  @inline final def swapState(oldState: State, newState: State): Boolean =
    Unsafe.instance.compareAndSwapObject(this, stateOffset, oldState, newState)

  /**
   * Helper method for accessing underlying state via Unsafe
   *
   * @return Reference to current state
   */
  @inline final def currentState: State =
    Unsafe.instance.getObjectVolatile(this, stateOffset).asInstanceOf[State]

  /**
   * Helper method for updating the underlying resetTimeout via Unsafe
   */
  @inline final def swapResetTimeout(oldResetTimeout: FiniteDuration, newResetTimeout: FiniteDuration): Boolean =
    Unsafe.instance.compareAndSwapObject(
      this,
      resetTimeoutOffset,
      oldResetTimeout,
      newResetTimeout)

  /**
   * Helper method for accessing to the underlying resetTimeout via Unsafe
   */
  @inline final def currentResetTimeout: FiniteDuration =
    Unsafe.instance.getObjectVolatile(this, resetTimeoutOffset).asInstanceOf[FiniteDuration]

}

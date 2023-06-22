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

package org.apache.pekko.pattern;

import org.apache.pekko.util.Unsafe;

class AbstractCircuitBreaker {
  protected static final long stateOffset;
  protected static final long resetTimeoutOffset;

  static {
    try {
      stateOffset =
          Unsafe.instance.objectFieldOffset(
              CircuitBreaker.class.getDeclaredField("_currentStateDoNotCallMeDirectly"));
      resetTimeoutOffset =
          Unsafe.instance.objectFieldOffset(
              CircuitBreaker.class.getDeclaredField("_currentResetTimeoutDoNotCallMeDirectly"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.apache.pekko.pattern.CircuitBreaker.State;
import scala.concurrent.duration.FiniteDuration;

class AbstractCircuitBreaker {
  protected static final VarHandle stateHandle;
  protected static final VarHandle resetTimeoutHandle;

  static {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(CircuitBreaker.class, MethodHandles.lookup());
      stateHandle =
          lookup.findVarHandle(
              CircuitBreaker.class, "_currentStateDoNotCallMeDirectly", State.class);
      resetTimeoutHandle =
          lookup.findVarHandle(
              CircuitBreaker.class,
              "_currentResetTimeoutDoNotCallMeDirectly",
              FiniteDuration.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

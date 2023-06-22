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

final class AbstractPromiseActorRef {
  static final long stateOffset;
  static final long watchedByOffset;

  static {
    try {
      stateOffset =
          Unsafe.instance.objectFieldOffset(
              PromiseActorRef.class.getDeclaredField("_stateDoNotCallMeDirectly"));
      watchedByOffset =
          Unsafe.instance.objectFieldOffset(
              PromiseActorRef.class.getDeclaredField("_watchedByDoNotCallMeDirectly"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

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

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import org.apache.pekko.util.Unsafe;

final class AbstractActorRef {
  static final long cellOffset;
  static final long lookupOffset;

  static {
    try {
      cellOffset =
          Unsafe.instance.objectFieldOffset(
              RepointableActorRef.class.getDeclaredField("_cellDoNotCallMeDirectly"));
      lookupOffset =
          Unsafe.instance.objectFieldOffset(
              RepointableActorRef.class.getDeclaredField("_lookupDoNotCallMeDirectly"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

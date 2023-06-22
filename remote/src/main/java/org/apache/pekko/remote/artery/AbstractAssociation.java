/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery;

import org.apache.pekko.util.Unsafe;

class AbstractAssociation {
  protected static final long sharedStateOffset;

  static {
    try {
      sharedStateOffset =
          Unsafe.instance.objectFieldOffset(
              Association.class.getDeclaredField("_sharedStateDoNotCallMeDirectly"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

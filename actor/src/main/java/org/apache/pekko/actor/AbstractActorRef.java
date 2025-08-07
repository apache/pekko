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

package org.apache.pekko.actor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import org.apache.pekko.actor.Cell;

final class AbstractActorRef {
  static final VarHandle cellHandle;
  static final VarHandle lookupHandle;

  static {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(RepointableActorRef.class, MethodHandles.lookup());

      cellHandle =
          lookup.findVarHandle(RepointableActorRef.class, "_cellDoNotCallMeDirectly", Cell.class);
      lookupHandle =
          lookup.findVarHandle(RepointableActorRef.class, "_lookupDoNotCallMeDirectly", Cell.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

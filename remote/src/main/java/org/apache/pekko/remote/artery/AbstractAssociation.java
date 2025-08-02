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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class AbstractAssociation {
  protected static final VarHandle sharedStateHandle;

  static {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(Association.class, MethodHandles.lookup());
      sharedStateHandle =
          lookup.findVarHandle(
              Association.class, "_sharedStateDoNotCallMeDirectly", AssociationState.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

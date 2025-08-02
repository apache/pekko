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

package org.apache.pekko.dispatch;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

abstract class AbstractMessageDispatcher {
    final static VarHandle shutdownScheduleHandle;
    final static VarHandle inhabitantsHandle;

    static {
        try {
            MethodHandles.Lookup lookup =
              MethodHandles.privateLookupIn(MessageDispatcher.class, MethodHandles.lookup());
            shutdownScheduleHandle = lookup.findVarHandle(
                MessageDispatcher.class,
                "_shutdownScheduleDoNotCallMeDirectly",
                int.class);
            inhabitantsHandle = lookup.findVarHandle(
                MessageDispatcher.class,
                "_inhabitantsDoNotCallMeDirectly",
                long.class);
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }
}

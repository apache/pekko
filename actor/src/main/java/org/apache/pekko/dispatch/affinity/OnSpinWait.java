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

package org.apache.pekko.dispatch.affinity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.apache.pekko.annotation.InternalApi;
import static java.lang.invoke.MethodType.methodType;

/**
 * INTERNAL API
 */
@InternalApi
final class OnSpinWait {
    private final static MethodHandle handle;

    public final static void spinWait() throws Throwable {
        handle.invoke(); // Will be inlined as an invokeExact since the callsite matches the MH definition of () -> void
    }

    static {
        final MethodHandle noop = MethodHandles.constant(Object.class, null).asType(methodType(Void.TYPE));
        MethodHandle impl;
        try {
          impl = MethodHandles.lookup().findStatic(Thread.class, "onSpinWait", methodType(Void.TYPE));
        } catch (NoSuchMethodException nsme) {
          impl = noop;
        } catch (IllegalAccessException iae) {
          impl = noop;
        }
        handle = impl;
  };
}
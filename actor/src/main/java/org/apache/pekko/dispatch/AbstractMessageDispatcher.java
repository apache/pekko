/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch;

import org.apache.pekko.util.Unsafe;

abstract class AbstractMessageDispatcher {
    final static long shutdownScheduleOffset;
    final static long inhabitantsOffset;

    static {
        try {
          shutdownScheduleOffset = Unsafe.instance.objectFieldOffset(MessageDispatcher.class.getDeclaredField("_shutdownScheduleDoNotCallMeDirectly"));
          inhabitantsOffset = Unsafe.instance.objectFieldOffset(MessageDispatcher.class.getDeclaredField("_inhabitantsDoNotCallMeDirectly"));
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }
}

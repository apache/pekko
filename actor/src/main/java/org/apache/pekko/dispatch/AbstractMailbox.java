/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch;

import org.apache.pekko.util.Unsafe;

final class AbstractMailbox {
    final static long mailboxStatusOffset;
    final static long systemMessageOffset;

    static {
        try {
          mailboxStatusOffset = Unsafe.instance.objectFieldOffset(Mailbox.class.getDeclaredField("_statusDoNotCallMeDirectly"));
          systemMessageOffset = Unsafe.instance.objectFieldOffset(Mailbox.class.getDeclaredField("_systemQueueDoNotCallMeDirectly"));
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }
}

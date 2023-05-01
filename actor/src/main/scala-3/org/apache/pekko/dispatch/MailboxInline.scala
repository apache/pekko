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

package org.apache.pekko.dispatch

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.Unsafe

@InternalApi
private[pekko] trait MailboxInline { this: Mailbox =>
  import Mailbox.Status

  inline final def currentStatus: Status =
    Unsafe.instance.getIntVolatile(this, AbstractMailbox.mailboxStatusOffset)

  inline protected final def updateStatus(oldStatus: Status, newStatus: Status): Boolean =
    Unsafe.instance.compareAndSwapInt(this, AbstractMailbox.mailboxStatusOffset, oldStatus, newStatus)

  inline protected final def setStatus(newStatus: Status): Unit =
    Unsafe.instance.putIntVolatile(this, AbstractMailbox.mailboxStatusOffset, newStatus)

}

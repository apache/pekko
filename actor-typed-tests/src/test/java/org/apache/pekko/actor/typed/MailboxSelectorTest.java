/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed;

public class MailboxSelectorTest {
  // Compile time only test to verify
  // mailbox factories are accessible from Java

  private MailboxSelector def = MailboxSelector.defaultMailbox();
  private MailboxSelector bounded = MailboxSelector.bounded(1000);
  private MailboxSelector conf = MailboxSelector.fromConfig("somepath");
}

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

package org.apache.pekko.actor.dungeon;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import org.apache.pekko.actor.ActorCell;
import org.apache.pekko.dispatch.Mailbox;
import org.apache.pekko.util.Unsafe;

final class AbstractActorCell {
  static final VarHandle mailboxHandle;
  static final VarHandle childrenHandle;
  static final VarHandle nextNameHandle;
  static final long functionRefsOffset;

  static {
    try {
      functionRefsOffset =
          Unsafe.instance.objectFieldOffset(
              ActorCell.class.getDeclaredField(
                  "org$apache$pekko$actor$dungeon$Children$$_functionRefsDoNotCallMeDirectly"));
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(ActorCell.class, MethodHandles.lookup());
      mailboxHandle =
          lookup.findVarHandle(
              ActorCell.class,
              "org$apache$pekko$actor$dungeon$Dispatch$$_mailboxDoNotCallMeDirectly",
              Mailbox.class);
      childrenHandle =
          lookup.findVarHandle(
              ActorCell.class,
              "org$apache$pekko$actor$dungeon$Children$$_childrenRefsDoNotCallMeDirectly",
              ChildrenContainer.class);
      nextNameHandle =
          lookup.findVarHandle(
              ActorCell.class,
              "org$apache$pekko$actor$dungeon$Children$$_nextNameDoNotCallMeDirectly",
              long.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

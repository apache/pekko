/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.dungeon;

import org.apache.pekko.actor.ActorCell;
import org.apache.pekko.util.Unsafe;

final class AbstractActorCell {
  static final long mailboxOffset;
  static final long childrenOffset;
  static final long nextNameOffset;
  static final long functionRefsOffset;

  static {
    try {
      mailboxOffset =
          Unsafe.instance.objectFieldOffset(
              ActorCell.class.getDeclaredField(
                  "org$apache$pekko$actor$dungeon$Dispatch$$_mailboxDoNotCallMeDirectly"));
      childrenOffset =
          Unsafe.instance.objectFieldOffset(
              ActorCell.class.getDeclaredField(
                  "org$apache$pekko$actor$dungeon$Children$$_childrenRefsDoNotCallMeDirectly"));
      nextNameOffset =
          Unsafe.instance.objectFieldOffset(
              ActorCell.class.getDeclaredField(
                  "org$apache$pekko$actor$dungeon$Children$$_nextNameDoNotCallMeDirectly"));
      functionRefsOffset =
          Unsafe.instance.objectFieldOffset(
              ActorCell.class.getDeclaredField(
                  "org$apache$pekko$actor$dungeon$Children$$_functionRefsDoNotCallMeDirectly"));
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }
}

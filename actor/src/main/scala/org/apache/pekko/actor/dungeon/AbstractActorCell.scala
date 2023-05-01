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

package org.apache.pekko.actor.dungeon

import org.apache.pekko.actor.ActorCell
import org.apache.pekko.util.Unsafe

object AbstractActorCell {
  private[dungeon] final var mailboxOffset = 0L
  private[dungeon] final var childrenOffset = 0L
  private[dungeon] final var nextNameOffset = 0L
  private[dungeon] final var functionRefsOffset = 0L

  try {
    mailboxOffset = Unsafe.instance.objectFieldOffset(
      classOf[ActorCell].getDeclaredField("org$apache$pekko$actor$dungeon$Dispatch$$_mailboxDoNotCallMeDirectly"))
    childrenOffset = Unsafe.instance.objectFieldOffset(classOf[ActorCell].getDeclaredField(
      "org$apache$pekko$actor$dungeon$Children$$_childrenRefsDoNotCallMeDirectly"))
    nextNameOffset = Unsafe.instance.objectFieldOffset(
      classOf[ActorCell].getDeclaredField("org$apache$pekko$actor$dungeon$Children$$_nextNameDoNotCallMeDirectly"))
    functionRefsOffset = Unsafe.instance.objectFieldOffset(classOf[ActorCell].getDeclaredField(
      "org$apache$pekko$actor$dungeon$Children$$_functionRefsDoNotCallMeDirectly"))
  } catch {
    case t: Throwable =>
      throw new ExceptionInInitializerError(t)
  }

}

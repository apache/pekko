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

package org.apache.pekko.pattern

import org.apache.pekko.util.Unsafe

object AbstractPromiseActorRef {
  private[pattern] final var stateOffset = 0L
  private[pattern] final var watchedByOffset = 0L

  try {
    stateOffset =
      Unsafe.instance.objectFieldOffset(classOf[PromiseActorRef].getDeclaredField("_stateDoNotCallMeDirectly"))
    watchedByOffset =
      Unsafe.instance.objectFieldOffset(classOf[PromiseActorRef].getDeclaredField("_watchedByDoNotCallMeDirectly"))
  } catch {
    case t: Throwable =>
      throw new ExceptionInInitializerError(t)
  }

}

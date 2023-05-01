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

package org.apache.pekko.pattern

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.actor.ActorRef
import pekko.pattern.AbstractPromiseActorRef.{ stateOffset, watchedByOffset }
import pekko.util.Unsafe

@InternalApi
private[pekko] trait PromiseActorRefInline { this: PromiseActorRef =>
  @inline final def watchedBy: Set[ActorRef] =
    Unsafe.instance.getObjectVolatile(this, watchedByOffset).asInstanceOf[Set[ActorRef]]

  @inline final def updateWatchedBy(oldWatchedBy: Set[ActorRef], newWatchedBy: Set[ActorRef]): Boolean =
    Unsafe.instance.compareAndSwapObject(this, watchedByOffset, oldWatchedBy, newWatchedBy)

  @inline final def state: AnyRef = Unsafe.instance.getObjectVolatile(this, stateOffset)

  @inline final def updateState(oldState: AnyRef, newState: AnyRef): Boolean =
    Unsafe.instance.compareAndSwapObject(this, stateOffset, oldState, newState)

  @inline final def setState(newState: AnyRef): Unit = Unsafe.instance.putObjectVolatile(this, stateOffset, newState)

}

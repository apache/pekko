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

package org.apache.pekko.actor.typed.internal.adapter

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.BehaviorInterceptor
import pekko.actor.typed.Signal
import pekko.actor.typed.TypedActorContext
import pekko.actor.typed.scaladsl.{ Behaviors, StashOverflowException }
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Messages to the user provided guardian must be deferred while the actor system is starting up. This
 * behavior delays starting the user provided behavior until the Start command is delivered from the actor
 * system, and we know that the bootstrap is completed and the actor context can be accessed.
 */
@InternalApi
private[pekko] object GuardianStartupBehavior {
  case object Start

  private val StashCapacity = 1000

  def apply[T](guardianBehavior: Behavior[T]): Behavior[Any] =
    waitingForStart(guardianBehavior, Vector.empty)

  private def waitingForStart[T](guardianBehavior: Behavior[T], tempStash: Vector[Any]): Behavior[Any] = {
    Behaviors.receiveMessage {
      case Start =>
        // ctx is not available initially so we cannot use it until here
        Behaviors.withStash[Any](StashCapacity) { stash =>
          tempStash.foreach(stash.stash)
          stash.unstashAll(Behaviors.intercept(() => new GuardianStopInterceptor)(guardianBehavior.unsafeCast[Any]))
        }
      case other =>
        if (tempStash.size >= StashCapacity) {
          throw new StashOverflowException("Guardian Behavior did not receive start and buffer is full.")
        }
        waitingForStart(guardianBehavior, tempStash :+ other)
    }
  }
}

/**
 * INTERNAL API
 *
 * When the user guardian is stopped the ActorSystem is terminated, but to run CoordinatedShutdown
 * as part of that we must intercept when the guardian is stopped and call ActorSystem.terminate()
 * explicitly.
 */
@InternalApi private[pekko] final class GuardianStopInterceptor extends BehaviorInterceptor[Any, Any] {
  override def aroundReceive(
      ctx: TypedActorContext[Any],
      msg: Any,
      target: BehaviorInterceptor.ReceiveTarget[Any]): Behavior[Any] = {
    val next = target(ctx, msg)
    interceptStopped(ctx, next)
  }

  override def aroundSignal(
      ctx: TypedActorContext[Any],
      signal: Signal,
      target: BehaviorInterceptor.SignalTarget[Any]): Behavior[Any] = {
    val next = target(ctx, signal)
    interceptStopped(ctx, next)
  }

  private def interceptStopped(ctx: TypedActorContext[Any], next: Behavior[Any]): Behavior[Any] = {
    if (Behavior.isAlive(next))
      next
    else {
      ctx.asScala.system.terminate()
      // return next so that the adapter can call post stop on the previous behavior
      next
    }
  }
}

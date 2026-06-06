/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed
package scaladsl

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.actor.typed.internal.adapter.{ PropsAdapter => _, _ }
import pekko.annotation.InternalApi

/**
 * Adapters between typed and classic actors and actor systems.
 * The underlying `ActorSystem` is the classic [[pekko.actor.ActorSystem]]
 * which runs Pekko Typed [[pekko.actor.typed.Behavior]] on an emulation layer. In this
 * system typed and classic actors can coexist.
 *
 * Use these adapters with `import org.apache.pekko.actor.typed.scaladsl.adapter._`.
 *
 * Implicit extension methods are added to classic and typed `ActorSystem`,
 * `ActorContext`. Such methods make it possible to create typed child actor
 * from classic parent actor, and the opposite classic child from typed parent.
 * `watch` is also supported in both directions.
 *
 * There is an implicit conversion from classic [[pekko.actor.ActorRef]] to
 * typed [[pekko.actor.typed.ActorRef]].
 *
 * There are also converters (`toTyped`, `toClassic`) from typed
 * [[pekko.actor.typed.ActorRef]] to classic [[pekko.actor.ActorRef]], and between classic
 * [[pekko.actor.ActorSystem]] and typed [[pekko.actor.typed.ActorSystem]].
 */
package object adapter {

  import language.implicitConversions

  /**
   * Extension methods added to [[pekko.actor.ActorSystem]].
   */
  implicit class ClassicActorSystemOps(val sys: pekko.actor.ActorSystem) extends AnyVal {

    /**
     *  Spawn the given behavior as a child of the user actor in a classic ActorSystem.
     *
     *  Typed actors default supervision strategy is to stop. Can be overridden with
     *  `Behaviors.supervise`.
     */
    def spawnAnonymous[T](behavior: Behavior[T], props: Props = Props.empty): ActorRef[T] = {
      ActorRefFactoryAdapter.spawnAnonymous(
        sys,
        Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop),
        props,
        rethrowTypedFailure = false)
    }

    /**
     *  Spawn the given behavior as a child of the user actor in a classic ActorSystem.
     *
     *  Typed actors default supervision strategy is to stop. Can be overridden with
     *  `Behaviors.supervise`.
     */
    def spawn[T](behavior: Behavior[T], name: String, props: Props = Props.empty): ActorRef[T] = {
      ActorRefFactoryAdapter.spawn(
        sys,
        Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop),
        name,
        props,
        rethrowTypedFailure = false)
    }

    def toTyped: ActorSystem[Nothing] = AdapterExtension(sys).adapter
  }

  /**
   * Extension methods added to [[pekko.actor.typed.ActorSystem]].
   */
  implicit class TypedActorSystemOps(val sys: ActorSystem[?]) extends AnyVal {
    def toClassic: pekko.actor.ActorSystem = sys.classicSystem

    /**
     * INTERNAL API
     */
    @InternalApi private[pekko] def internalSystemActorOf[U](
        behavior: Behavior[U],
        name: String,
        props: Props): ActorRef[U] = {
      toClassic.asInstanceOf[ExtendedActorSystem].systemActorOf(PropsAdapter(behavior, props), name)
    }
  }

  /**
   * Extension methods added to [[pekko.actor.ActorContext]].
   */
  implicit class ClassicActorContextOps(val ctx: pekko.actor.ActorContext) extends AnyVal {

    /**
     *  Spawn the given behavior as a child of the user actor in a classic ActorContext.
     *
     *  Typed actors default supervision strategy is to stop. Can be overridden with
     *  `Behaviors.supervise`.
     */
    def spawnAnonymous[T](behavior: Behavior[T], props: Props = Props.empty): ActorRef[T] =
      ActorRefFactoryAdapter.spawnAnonymous(
        ctx,
        Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop),
        props,
        rethrowTypedFailure = false)

    /**
     *  Spawn the given behavior as a child of the user actor in a classic ActorContext.
     *
     *  Typed actors default supervision strategy is to stop. Can be overridden with
     *  `Behaviors.supervise`.
     */
    def spawn[T](behavior: Behavior[T], name: String, props: Props = Props.empty): ActorRef[T] =
      ActorRefFactoryAdapter.spawn(
        ctx,
        Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop),
        name,
        props,
        rethrowTypedFailure = false)

    def watch[U](other: ActorRef[U]): Unit = ctx.watch(ActorRefAdapter.toClassic(other))
    def unwatch[U](other: ActorRef[U]): Unit = ctx.unwatch(ActorRefAdapter.toClassic(other))

    def stop(child: ActorRef[?]): Unit =
      ctx.stop(ActorRefAdapter.toClassic(child))
  }

  /**
   * Extension methods added to [[pekko.actor.typed.scaladsl.ActorContext]].
   */
  implicit class TypedActorContextOps(val ctx: scaladsl.ActorContext[?]) extends AnyVal {
    def actorOf(props: pekko.actor.Props): pekko.actor.ActorRef =
      ActorContextAdapter.toClassic(ctx).actorOf(props)

    def actorOf(props: pekko.actor.Props, name: String): pekko.actor.ActorRef =
      ActorContextAdapter.toClassic(ctx).actorOf(props, name)

    def toClassic: pekko.actor.ActorContext = ActorContextAdapter.toClassic(ctx)

    // watch, unwatch and stop not needed here because of the implicit ActorRef conversion
  }

  /**
   * Extension methods added to [[pekko.actor.typed.ActorRef]].
   */
  implicit class TypedActorRefOps(val ref: ActorRef[?]) extends AnyVal {
    def toClassic: pekko.actor.ActorRef = ActorRefAdapter.toClassic(ref)
  }

  /**
   * Extension methods added to [[pekko.actor.ActorRef]].
   */
  implicit class ClassicActorRefOps(val ref: pekko.actor.ActorRef) extends AnyVal {

    /**
     * Adapt the classic `ActorRef` to `org.apache.pekko.actor.typed.ActorRef[T]`. There is also an
     * automatic implicit conversion for this, but this more explicit variant might
     * sometimes be preferred.
     */
    def toTyped[T]: ActorRef[T] = ActorRefAdapter(ref)
  }

  /**
   * Implicit conversion from classic [[pekko.actor.ActorRef]] to [[pekko.actor.typed.ActorRef]].
   */
  implicit def actorRefAdapter[T](ref: pekko.actor.ActorRef): ActorRef[T] = ActorRefAdapter(ref)

  /**
   * Extension methods added to [[pekko.actor.typed.Scheduler]].
   */
  implicit class TypedSchedulerOps(val scheduler: Scheduler) extends AnyVal {
    def toClassic: pekko.actor.Scheduler = SchedulerAdapter.toClassic(scheduler)
  }

  /**
   * Extension methods added to [[pekko.actor.Scheduler]].
   */
  implicit class ClassicSchedulerOps(val scheduler: pekko.actor.Scheduler) extends AnyVal {

    /**
     * Adapt the classic `Scheduler` to `org.apache.pekko.actor.typed.Scheduler`.
     */
    def toTyped: Scheduler = new SchedulerAdapter(scheduler)
  }

}

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.javadsl

import org.apache.pekko
import pekko.actor
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Props
import pekko.actor.typed.Scheduler
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.internal.adapter.ActorContextAdapter
import pekko.actor.typed.scaladsl.adapter._
import pekko.japi.Creator

/**
 * Adapters between typed and classic actors and actor systems.
 * The underlying `ActorSystem` is the classic [[pekko.actor.ActorSystem]]
 * which runs Akka [[pekko.actor.typed.Behavior]] on an emulation layer. In this
 * system typed and classic actors can coexist.
 *
 * These methods make it possible to create a child actor from classic
 * parent actor, and the opposite classic child from typed parent.
 * `watch` is also supported in both directions.
 *
 * There are also converters (`toTyped`, `toClassic`) between classic
 * [[pekko.actor.ActorRef]] and [[pekko.actor.typed.ActorRef]], and between classic
 * [[pekko.actor.ActorSystem]] and [[pekko.actor.typed.ActorSystem]].
 */
object Adapter {

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorSystem.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawnAnonymous[T](sys: pekko.actor.ActorSystem, behavior: Behavior[T]): ActorRef[T] =
    spawnAnonymous(sys, behavior, Props.empty)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorSystem.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawnAnonymous[T](sys: pekko.actor.ActorSystem, behavior: Behavior[T], props: Props): ActorRef[T] =
    sys.spawnAnonymous(behavior, props)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorSystem.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawn[T](sys: pekko.actor.ActorSystem, behavior: Behavior[T], name: String): ActorRef[T] =
    spawn(sys, behavior, name, Props.empty)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorSystem.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawn[T](sys: pekko.actor.ActorSystem, behavior: Behavior[T], name: String, props: Props): ActorRef[T] =
    sys.spawn(behavior, name, props)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorContext.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawnAnonymous[T](ctx: pekko.actor.ActorContext, behavior: Behavior[T]): ActorRef[T] =
    spawnAnonymous(ctx, behavior, Props.empty)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorContext.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawnAnonymous[T](ctx: pekko.actor.ActorContext, behavior: Behavior[T], props: Props): ActorRef[T] =
    ctx.spawnAnonymous(behavior, props)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorContext.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawn[T](ctx: pekko.actor.ActorContext, behavior: Behavior[T], name: String): ActorRef[T] =
    spawn(ctx, behavior, name, Props.empty)

  /**
   *  Spawn the given behavior as a child of the user actor in a classic ActorContext.
   *  Actor default supervision strategy is to stop. Can be overridden with
   *  `Behaviors.supervise`.
   */
  def spawn[T](ctx: pekko.actor.ActorContext, behavior: Behavior[T], name: String, props: Props): ActorRef[T] =
    ctx.spawn(behavior, name, props)

  def toTyped(sys: pekko.actor.ActorSystem): ActorSystem[Void] =
    sys.toTyped.asInstanceOf[ActorSystem[Void]]

  def toClassic(sys: ActorSystem[_]): pekko.actor.ActorSystem =
    sys.toClassic

  def toClassic(ctx: ActorContext[_]): actor.ActorContext =
    ActorContextAdapter.toClassic(ctx)

  def watch[U](ctx: pekko.actor.ActorContext, other: ActorRef[U]): Unit =
    ctx.watch(other)

  def unwatch[U](ctx: pekko.actor.ActorContext, other: ActorRef[U]): Unit =
    ctx.unwatch(other)

  def stop(ctx: pekko.actor.ActorContext, child: ActorRef[_]): Unit =
    ctx.stop(child)

  def watch[U](ctx: ActorContext[_], other: pekko.actor.ActorRef): Unit =
    ctx.watch(other)

  def unwatch[U](ctx: ActorContext[_], other: pekko.actor.ActorRef): Unit =
    ctx.unwatch(other)

  def stop(ctx: ActorContext[_], child: pekko.actor.ActorRef): Unit =
    ctx.stop(child)

  def actorOf(ctx: ActorContext[_], props: pekko.actor.Props): pekko.actor.ActorRef =
    ActorContextAdapter.toClassic(ctx).actorOf(props)

  def actorOf(ctx: ActorContext[_], props: pekko.actor.Props, name: String): pekko.actor.ActorRef =
    ActorContextAdapter.toClassic(ctx).actorOf(props, name)

  def toClassic(ref: ActorRef[_]): pekko.actor.ActorRef =
    ref.toClassic

  def toTyped[T](ref: pekko.actor.ActorRef): ActorRef[T] =
    ref

  /**
   * Wrap [[pekko.actor.typed.Behavior]] in a classic [[pekko.actor.Props]], i.e. when
   * spawning a typed child actor from a classic parent actor.
   * This is normally not needed because you can use the extension methods
   * `spawn` and `spawnAnonymous` with a classic `ActorContext`, but it's needed
   * when using typed actors with an existing library/tool that provides an API that
   * takes a classic [[pekko.actor.Props]] parameter. Cluster Sharding is an
   * example of that.
   */
  def props[T](behavior: Creator[Behavior[T]], deploy: Props): pekko.actor.Props =
    pekko.actor.typed.internal.adapter.PropsAdapter(
      () => Behaviors.supervise(behavior.create()).onFailure(SupervisorStrategy.stop),
      deploy,
      rethrowTypedFailure = false)

  /**
   * Wrap [[pekko.actor.typed.Behavior]] in a classic [[pekko.actor.Props]], i.e. when
   * spawning a typed child actor from a classic parent actor.
   * This is normally not needed because you can use the extension methods
   * `spawn` and `spawnAnonymous` with a classic `ActorContext`, but it's needed
   * when using typed actors with an existing library/tool that provides an API that
   * takes a classic [[pekko.actor.Props]] parameter. Cluster Sharding is an
   * example of that.
   */
  def props[T](behavior: Creator[Behavior[T]]): pekko.actor.Props =
    props(behavior, Props.empty)

  def toClassic(scheduler: Scheduler): pekko.actor.Scheduler =
    scheduler.toClassic

  def toTyped[T](scheduler: pekko.actor.Scheduler): Scheduler =
    scheduler.toTyped
}

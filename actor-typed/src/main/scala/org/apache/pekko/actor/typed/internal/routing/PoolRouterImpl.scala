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

package org.apache.pekko.actor.typed.internal.routing

import org.apache.pekko
import pekko.actor.typed._
import pekko.actor.typed.javadsl.PoolRouter
import pekko.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import pekko.annotation.InternalApi
import pekko.util.ConstantFun

import java.util.function
import java.util.function.Predicate

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class PoolRouterBuilder[T](
    poolSize: Int,
    behavior: Behavior[T],
    logicFactory: ActorSystem[_] => RoutingLogic[T] = (_: ActorSystem[_]) => new RoutingLogics.RoundRobinLogic[T],
    broadcastPredicate: T => Boolean = ConstantFun.anyToFalse,
    routeeProps: Props = Props.empty)
    extends javadsl.PoolRouter[T]
    with scaladsl.PoolRouter[T] {
  if (poolSize < 1) throw new IllegalArgumentException(s"pool size must be positive, was $poolSize")

  // deferred creation of the actual router
  def apply(ctx: TypedActorContext[T]): Behavior[T] =
    new PoolRouterImpl[T](
      ctx.asScala,
      poolSize,
      behavior,
      logicFactory(ctx.asScala.system),
      broadcastPredicate,
      routeeProps)

  def withRandomRouting(): PoolRouterBuilder[T] = copy(logicFactory = _ => new RoutingLogics.RandomLogic[T]())

  def withRoundRobinRouting(): PoolRouterBuilder[T] = copy(logicFactory = _ => new RoutingLogics.RoundRobinLogic[T])

  def withConsistentHashingRouting(virtualNodesFactor: Int, mapping: function.Function[T, String]): PoolRouter[T] =
    withConsistentHashingRouting(virtualNodesFactor, mapping.apply(_))

  def withConsistentHashingRouting(virtualNodesFactor: Int, mapping: T => String): PoolRouterBuilder[T] =
    copy(
      logicFactory = system => new RoutingLogics.ConsistentHashingLogic[T](virtualNodesFactor, mapping, system.address))

  def withPoolSize(poolSize: Int): PoolRouterBuilder[T] = copy(poolSize = poolSize)

  def withRouteeProps(routeeProps: Props): PoolRouterBuilder[T] = copy(routeeProps = routeeProps)

  override def withBroadcastPredicate(predicate: Predicate[T]): PoolRouter[T] =
    copy(broadcastPredicate = value => predicate.test(value))

  override def withBroadcastPredicate(predicate: T => Boolean): scaladsl.PoolRouter[T] =
    copy(broadcastPredicate = predicate)
}

/**
 * INTERNAL API
 */
@InternalApi
private final class PoolRouterImpl[T](
    ctx: ActorContext[T],
    poolSize: Int,
    behavior: Behavior[T],
    logic: RoutingLogic[T],
    broadcastPredicate: T => Boolean,
    routeeProps: Props)
    extends AbstractBehavior[T](ctx) {

  (1 to poolSize).foreach { _ =>
    val child = context.spawnAnonymous(behavior, routeeProps)
    context.watch(child)
    child
  }
  onRouteesChanged()

  private def onRouteesChanged(): Unit = {
    val children = context.children.toSet.asInstanceOf[Set[ActorRef[T]]]
    logic.routeesUpdated(children)
  }

  def onMessage(msg: T): Behavior[T] = {
    if ((broadcastPredicate ne ConstantFun.anyToFalse) && broadcastPredicate(msg)) {
      ctx.children.foreach(_.unsafeUpcast ! msg)
    } else {
      logic.selectRoutee(msg) ! msg
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case Terminated(child) =>
      // Note that if several children are stopping concurrently children may already be empty
      // for the `Terminated` we receive for the first child. This means it is not certain that
      // there will be a log entry per child in those cases (it does not make sense to keep the
      // pool alive just to get the logging right when there are no routees available)
      if (context.children.nonEmpty) {
        context.log.debug("Pool child stopped [{}]", child.path)
        onRouteesChanged()
        this
      } else {
        context.log.info("Last pool child stopped, stopping pool [{}]", context.self.path)
        Behaviors.stopped
      }
  }

}

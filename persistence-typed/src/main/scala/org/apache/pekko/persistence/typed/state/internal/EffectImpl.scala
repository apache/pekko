/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.internal

import scala.collection.immutable

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.annotation.InternalApi
import pekko.persistence.typed.state.{ javadsl, scaladsl }

/** INTERNAL API */
@InternalApi
private[pekko] abstract class EffectImpl[State]
    extends javadsl.EffectBuilder[State]
    with javadsl.ReplyEffect[State]
    with scaladsl.ReplyEffect[State]
    with scaladsl.EffectBuilder[State] {
  /* The state that will be persisted in this effect */
  override def state: Option[State] = None

  override def thenRun(chainedEffect: State => Unit): EffectImpl[State] =
    CompositeEffect(this, new Callback[State](chainedEffect))

  override def thenReply[ReplyMessage](replyTo: ActorRef[ReplyMessage])(
      replyWithMessage: State => ReplyMessage): EffectImpl[State] =
    CompositeEffect(this, new ReplyEffectImpl[ReplyMessage, State](replyTo, replyWithMessage))

  override def thenUnstashAll(): EffectImpl[State] =
    CompositeEffect(this, UnstashAll.asInstanceOf[SideEffect[State]])

  override def thenNoReply(): EffectImpl[State] =
    CompositeEffect(this, new NoReplyEffectImpl[State])

  override def thenStop(): EffectImpl[State] =
    CompositeEffect(this, Stop.asInstanceOf[SideEffect[State]])

}

/** INTERNAL API */
@InternalApi
private[pekko] object CompositeEffect {
  def apply[State](effect: scaladsl.EffectBuilder[State], sideEffects: SideEffect[State]): CompositeEffect[State] =
    CompositeEffect[State](effect, sideEffects :: Nil)
}

/** INTERNAL API */
@InternalApi
private[pekko] final case class CompositeEffect[State](
    persistingEffect: scaladsl.EffectBuilder[State],
    _sideEffects: immutable.Seq[SideEffect[State]])
    extends EffectImpl[State] {

  override val state: Option[State] = persistingEffect.state

  override def toString: String =
    s"CompositeEffect($persistingEffect, sideEffects: ${_sideEffects.size})"
}

/** INTERNAL API */
@InternalApi
private[pekko] case object PersistNothing extends EffectImpl[Nothing]

/** INTERNAL API */
@InternalApi
private[pekko] final case class Persist[State](newState: State) extends EffectImpl[State] {
  override val state: Option[State] = Option(newState)

  override def toString: String = s"Persist(${newState.getClass.getName})"
}

/** INTERNAL API */
@InternalApi
private[pekko] case class Delete[State]() extends EffectImpl[State]

/** INTERNAL API */
@InternalApi
private[pekko] case object Unhandled extends EffectImpl[Nothing]

/** INTERNAL API */
@InternalApi
private[pekko] case object Stash extends EffectImpl[Nothing]

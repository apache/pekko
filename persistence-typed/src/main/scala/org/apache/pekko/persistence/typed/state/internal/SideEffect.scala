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

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.annotation.InternalApi

/**
 * A [[SideEffect]] is an side effect that can be chained after a main effect.
 *
 * Persist, none and unhandled are main effects. Then any number of
 * call backs can be added to these effects.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] sealed abstract class SideEffect[State]

/** INTERNAL API */
@InternalApi
private[pekko] class Callback[State](val sideEffect: State => Unit) extends SideEffect[State] {
  override def toString: String = "Callback"
}

/** INTERNAL API */
@InternalApi
final private[pekko] class ReplyEffectImpl[ReplyMessage, State](
    replyTo: ActorRef[ReplyMessage],
    replyWithMessage: State => ReplyMessage)
    extends Callback[State](state => replyTo ! replyWithMessage(state)) {
  override def toString: String = "Reply"
}

/** INTERNAL API */
@InternalApi
final private[pekko] class NoReplyEffectImpl[State] extends Callback[State](_ => ()) {
  override def toString: String = "NoReply"
}

/** INTERNAL API */
@InternalApi
private[pekko] case object Stop extends SideEffect[Nothing]

/** INTERNAL API */
@InternalApi
private[pekko] case object UnstashAll extends SideEffect[Nothing]

/** INTERNAL API */
@InternalApi
private[pekko] object SideEffect {

  def apply[State](callback: State => Unit): SideEffect[State] =
    new Callback(callback)

  def unstashAll[State](): SideEffect[State] = UnstashAll.asInstanceOf[SideEffect[State]]
}

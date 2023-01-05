/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.eventstream

import scala.reflect.ClassTag
import org.apache.pekko
import pekko.actor.InvalidMessageException
import pekko.actor.typed.ActorRef
import pekko.annotation.{ DoNotInherit, InternalApi }

object EventStream {

  /**
   * The set of commands accepted by the [[pekko.actor.typed.ActorSystem.eventStream]].
   *
   * Not for user Extension
   */
  @DoNotInherit sealed trait Command

  /**
   * Publish an event of type E by sending this command to
   * the [[pekko.actor.typed.ActorSystem.eventStream]].
   */
  final case class Publish[E](event: E) extends Command {
    if (event == null)
      throw InvalidMessageException("[null] is not an allowed event")
  }

  /**
   * Subscribe a typed actor to listen for types or subtypes of E
   * by sending this command to the [[pekko.actor.typed.ActorSystem.eventStream]].
   *
   * ==Simple example==
   * {{{
   *   sealed trait A
   *   case object A1 extends A
   *   //listen for all As
   *   def subscribe(actorSystem: ActorSystem[_], actorRef: ActorRef[A]) =
   *     actorSystem.eventStream ! EventStream.Subscribe(actorRef)
   *   //listen for A1s only
   *   def subscribe(actorSystem: ActorSystem[_], actorRef: ActorRef[A]) =
   *     actorSystem.eventStream ! EventStream.Subscribe[A1](actorRef)
   * }}}
   */
  final case class Subscribe[E](subscriber: ActorRef[E])(implicit classTag: ClassTag[E]) extends Command {

    /**
     * Java API.
     */
    def this(clazz: Class[E], subscriber: ActorRef[E]) = this(subscriber)(ClassTag(clazz))

    /**
     * INTERNAL API
     */
    @InternalApi private[pekko] def topic: Class[_] = classTag.runtimeClass
  }

  /**
   * Unsubscribe an actor ref from the event stream
   * by sending this command to the [[pekko.actor.typed.ActorSystem.eventStream]].
   */
  final case class Unsubscribe[E](subscriber: ActorRef[E]) extends Command

}

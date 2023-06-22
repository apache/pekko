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

package org.apache.pekko.routing

import java.util.{ Set, TreeSet }

import org.apache.pekko.actor.{ Actor, ActorRef }

sealed trait ListenerMessage
final case class Listen(listener: ActorRef) extends ListenerMessage
final case class Deafen(listener: ActorRef) extends ListenerMessage
final case class WithListeners(f: (ActorRef) => Unit) extends ListenerMessage

/**
 * Listeners is a generic trait to implement listening capability on an Actor.
 * <p/>
 * Use the <code>gossip(msg)</code> method to have it sent to the listeners.
 * <p/>
 * Send <code>Listen(self)</code> to start listening.
 * <p/>
 * Send <code>Deafen(self)</code> to stop listening.
 * <p/>
 * Send <code>WithListeners(fun)</code> to traverse the current listeners.
 */
trait Listeners { self: Actor =>
  protected val listeners: Set[ActorRef] = new TreeSet[ActorRef]

  /**
   * Chain this into the receive function.
   *
   * {{{ def receive = listenerManagement orElse … }}}
   */
  protected def listenerManagement: Actor.Receive = {
    case Listen(l) => listeners.add(l)
    case Deafen(l) => listeners.remove(l)
    case WithListeners(f) =>
      val i = listeners.iterator
      while (i.hasNext) f(i.next)
  }

  /**
   * Sends the supplied message to all current listeners using the provided sender() as sender.
   */
  protected def gossip(msg: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    val i = listeners.iterator
    while (i.hasNext) i.next ! msg
  }
}

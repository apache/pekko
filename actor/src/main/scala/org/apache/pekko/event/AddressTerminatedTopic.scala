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

package org.apache.pekko.event

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.AddressTerminated
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider

/**
 * INTERNAL API
 *
 * Watchers of remote actor references register themselves as subscribers
 * of [[pekko.actor.AddressTerminated]] notifications. Remote and cluster
 * death watch publish `AddressTerminated` when a remote system is deemed
 * dead.
 */
private[pekko] object AddressTerminatedTopic extends ExtensionId[AddressTerminatedTopic] with ExtensionIdProvider {
  override def get(system: ActorSystem): AddressTerminatedTopic = super.get(system)
  override def get(system: ClassicActorSystemProvider): AddressTerminatedTopic = super.get(system)

  override def lookup = AddressTerminatedTopic

  override def createExtension(system: ExtendedActorSystem): AddressTerminatedTopic =
    new AddressTerminatedTopic
}

/**
 * INTERNAL API
 */
private[pekko] final class AddressTerminatedTopic extends Extension {

  private val subscribers = new AtomicReference[Set[ActorRef]](Set.empty[ActorRef])

  @tailrec def subscribe(subscriber: ActorRef): Unit = {
    val current = subscribers.get
    if (!subscribers.compareAndSet(current, current + subscriber))
      subscribe(subscriber) // retry
  }

  @tailrec def unsubscribe(subscriber: ActorRef): Unit = {
    val current = subscribers.get
    if (!subscribers.compareAndSet(current, current - subscriber))
      unsubscribe(subscriber) // retry
  }

  def publish(msg: AddressTerminated): Unit = {
    subscribers.get.foreach { _.tell(msg, ActorRef.noSender) }
  }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue

import org.apache.pekko
import pekko.dispatch.BoundedMessageQueueSemantics
import pekko.dispatch.BoundedNodeMessageQueue
import pekko.dispatch.Envelope
import pekko.dispatch.MailboxType
import pekko.dispatch.MessageQueue
import pekko.dispatch.ProducesMessageQueue

import com.typesafe.config.Config

/**
 * ManyToOneArrayMailbox is a high-performance, multiple-producer single-consumer, bounded MailboxType,
 * Noteworthy is that it discards overflow as DeadLetters.
 *
 * It can't have multiple consumers, which rules out using it with BalancingPool (BalancingDispatcher) for instance.
 *
 * NOTE: ManyToOneArrayMailbox does not use `mailbox-push-timeout-time` as it is non-blocking.
 */
case class ManyToOneArrayMailbox(val capacity: Int)
    extends MailboxType
    with ProducesMessageQueue[BoundedNodeMessageQueue] {

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  if (capacity < 0) throw new IllegalArgumentException("The capacity for ManyToOneArrayMailbox can not be negative")

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
    new ManyToOneArrayMessageQueue(capacity)
}

/**
 * Lock-free bounded non-blocking multiple-producer single-consumer queue.
 * Discards overflowing messages into DeadLetters.
 * Allocation free, using `org.agrona.concurrent.ManyToOneConcurrentArrayQueue`.
 */
class ManyToOneArrayMessageQueue(capacity: Int) extends MessageQueue with BoundedMessageQueueSemantics {
  final def pushTimeOut: Duration = Duration.Undefined

  private val queue = new ManyToOneConcurrentArrayQueue[Envelope](capacity)

  final def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!queue.add(handle))
      receiver
        .asInstanceOf[InternalActorRef]
        .provider
        .deadLetters
        .tell(DeadLetter(handle.message, handle.sender, receiver), handle.sender)

  final def dequeue(): Envelope = queue.poll()

  final def numberOfMessages: Int = queue.size()

  final def hasMessages: Boolean = !queue.isEmpty()

  @tailrec final def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val envelope = dequeue()
    if (envelope ne null) {
      deadLetters.enqueue(owner, envelope)
      cleanUp(owner, deadLetters)
    }
  }
}

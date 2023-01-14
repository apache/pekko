/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

import com.typesafe.config.Config
import org.jctools.queues.MpscGrowableArrayQueue

import org.apache.pekko
import pekko.dispatch.BoundedMessageQueueSemantics
import pekko.dispatch.BoundedNodeMessageQueue
import pekko.dispatch.Envelope
import pekko.dispatch.MailboxType
import pekko.dispatch.MessageQueue
import pekko.dispatch.ProducesMessageQueue

case class JCToolsMailbox(val capacity: Int) extends MailboxType with ProducesMessageQueue[BoundedNodeMessageQueue] {

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))

  if (capacity < 0) throw new IllegalArgumentException("The capacity for JCToolsMailbox can not be negative")

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
    new JCToolsMessageQueue(capacity)
}

class JCToolsMessageQueue(capacity: Int)
    extends MpscGrowableArrayQueue[Envelope](capacity)
    with MessageQueue
    with BoundedMessageQueueSemantics {
  final def pushTimeOut: Duration = Duration.Undefined

  final def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (!offer(handle))
      receiver
        .asInstanceOf[InternalActorRef]
        .provider
        .deadLetters
        .tell(DeadLetter(handle.message, handle.sender, receiver), handle.sender)

  final def dequeue(): Envelope = poll()

  final def numberOfMessages: Int = size()

  final def hasMessages: Boolean = !isEmpty()

  @tailrec final def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    val envelope = dequeue()
    if (envelope ne null) {
      deadLetters.enqueue(owner, envelope)
      cleanUp(owner, deadLetters)
    }
  }
}

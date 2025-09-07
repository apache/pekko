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

package org.apache.pekko.dispatch

import java.util.{ Comparator, Iterator }
import java.util.concurrent.ConcurrentSkipListSet

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorCell
import pekko.actor.ActorSystemImpl
import pekko.dispatch.sysmsg._
import pekko.util.Helpers

/**
 * INTERNAL API: Use `BalancingPool` instead of this dispatcher directly.
 *
 * An executor based event driven dispatcher which will try to redistribute work from busy actors to idle actors. It is assumed
 * that all actors using the same instance of this dispatcher can process all messages that have been sent to one of the actors. I.e. the
 * actors belong to a pool of actors, and to the client there is no guarantee about which actor instance actually processes a given message.
 * <p/>
 * Although the technique used in this implementation is commonly known as "work stealing", the actual implementation is probably
 * best described as "work donating" because the actor of which work is being stolen takes the initiative.
 * <p/>
 * The preferred way of creating dispatchers is to define configuration of it and use the
 * the `lookup` method in [[pekko.dispatch.Dispatchers]].
 *
 * @see org.apache.pekko.dispatch.BalancingDispatcher
 * @see org.apache.pekko.dispatch.Dispatchers
 */
@deprecated("Use BalancingPool instead of BalancingDispatcher", "Akka 2.3")
private[pekko] class BalancingDispatcher(
    _configurator: MessageDispatcherConfigurator,
    _id: String,
    throughput: Int,
    throughputDeadlineTime: Duration,
    _mailboxType: MailboxType,
    _executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
    _shutdownTimeout: FiniteDuration,
    attemptTeamWork: Boolean)
    extends Dispatcher(
      _configurator,
      _id,
      throughput,
      throughputDeadlineTime,
      _executorServiceFactoryProvider,
      _shutdownTimeout) {

  /**
   * INTERNAL API
   */
  private[pekko] val team =
    new ConcurrentSkipListSet[ActorCell](Helpers.identityHashComparator(new Comparator[ActorCell] {
      def compare(l: ActorCell, r: ActorCell) = l.self.path.compareTo(r.self.path)
    }))

  /**
   * INTERNAL API
   */
  private[pekko] val messageQueue: MessageQueue = _mailboxType.create(None, None)

  private class SharingMailbox(val system: ActorSystemImpl, _messageQueue: MessageQueue)
      extends Mailbox(_messageQueue)
      with DefaultSystemMessageQueue {
    override def cleanUp(): Unit = {
      val dlq = mailboxes.deadLetterMailbox
      // Don't call the original implementation of this since it scraps all messages, and we don't want to do that
      var messages = systemDrain(new LatestFirstSystemMessageList(NoMessage))
      while (messages.nonEmpty) {
        // message must be “virgin” before being able to systemEnqueue again
        val message = messages.head
        messages = messages.tail
        message.unlink()
        dlq.systemEnqueue(system.deadLetters, message)
      }
    }
  }

  protected[pekko] override def createMailbox(actor: pekko.actor.Cell, mailboxType: MailboxType): Mailbox =
    new SharingMailbox(actor.systemImpl, messageQueue)

  protected[pekko] override def register(actor: ActorCell): Unit = {
    super.register(actor)
    team.add(actor)
  }

  protected[pekko] override def unregister(actor: ActorCell): Unit = {
    team.remove(actor)
    super.unregister(actor)
    teamWork()
  }

  override protected[pekko] def dispatch(receiver: ActorCell, invocation: Envelope) = {
    messageQueue.enqueue(receiver.self, invocation)
    if (!registerForExecution(receiver.mailbox, hasMessageHint = false, hasSystemMessageHint = false)) teamWork()
  }

  protected def teamWork(): Unit =
    if (attemptTeamWork) {
      @tailrec def scheduleOne(i: Iterator[ActorCell] = team.iterator): Unit =
        if (messageQueue.hasMessages
          && i.hasNext
          && (executorService.executor match {
            case lm: LoadMetrics => !lm.atFullThrottle()
            case _               => true
          })
          && !registerForExecution(i.next.mailbox, hasMessageHint = false, hasSystemMessageHint = false))
          scheduleOne(i)

      scheduleOne()
    }
}

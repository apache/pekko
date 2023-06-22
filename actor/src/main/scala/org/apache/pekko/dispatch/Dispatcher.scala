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

import java.util.concurrent.{ ExecutorService, RejectedExecutionException }
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.ActorCell
import pekko.dispatch.sysmsg.SystemMessage
import pekko.event.Logging
import pekko.event.Logging.Error

/**
 * The event-based ``Dispatcher`` binds a set of Actors to a thread pool backed up by a
 * `BlockingQueue`.
 *
 * The preferred way of creating dispatchers is to define configuration of it and use the
 * the `lookup` method in [[pekko.dispatch.Dispatchers]].
 *
 * @param throughput positive integer indicates the dispatcher will only process so much messages at a time from the
 *                   mailbox, without checking the mailboxes of other actors. Zero or negative means the dispatcher
 *                   always continues until the mailbox is empty.
 *                   Larger values (or zero or negative) increase throughput, smaller values increase fairness
 */
class Dispatcher(
    _configurator: MessageDispatcherConfigurator,
    val id: String,
    val throughput: Int,
    val throughputDeadlineTime: Duration,
    executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
    val shutdownTimeout: FiniteDuration)
    extends MessageDispatcher(_configurator) {

  import configurator.prerequisites._

  private class LazyExecutorServiceDelegate(factory: ExecutorServiceFactory) extends ExecutorServiceDelegate {
    lazy val executor: ExecutorService = factory.createExecutorService
    def copy(): LazyExecutorServiceDelegate = new LazyExecutorServiceDelegate(factory)
  }

  /**
   * At first glance this var does not seem to be updated anywhere, but in
   * fact it is, via the esUpdater [[AtomicReferenceFieldUpdater]] below.
   */
  @nowarn("msg=never updated")
  @volatile private var executorServiceDelegate: LazyExecutorServiceDelegate =
    new LazyExecutorServiceDelegate(executorServiceFactoryProvider.createExecutorServiceFactory(id, threadFactory))

  protected final def executorService: ExecutorServiceDelegate = executorServiceDelegate

  /**
   * INTERNAL API
   */
  protected[pekko] def dispatch(receiver: ActorCell, invocation: Envelope): Unit = {
    val mbox = receiver.mailbox
    mbox.enqueue(receiver.self, invocation)
    registerForExecution(mbox, true, false)
  }

  /**
   * INTERNAL API
   */
  protected[pekko] def systemDispatch(receiver: ActorCell, invocation: SystemMessage): Unit = {
    val mbox = receiver.mailbox
    mbox.systemEnqueue(receiver.self, invocation)
    registerForExecution(mbox, false, true)
  }

  /**
   * INTERNAL API
   */
  protected[pekko] def executeTask(invocation: TaskInvocation): Unit = {
    try {
      executorService.execute(invocation)
    } catch {
      case e: RejectedExecutionException =>
        try {
          executorService.execute(invocation)
        } catch {
          case e2: RejectedExecutionException =>
            eventStream.publish(Error(e, getClass.getName, getClass, "executeTask was rejected twice!"))
            throw e2
        }
    }
  }

  /**
   * INTERNAL API
   */
  protected[pekko] def createMailbox(actor: pekko.actor.Cell, mailboxType: MailboxType): Mailbox = {
    new Mailbox(mailboxType.create(Some(actor.self), Some(actor.system))) with DefaultSystemMessageQueue
  }

  private val esUpdater = AtomicReferenceFieldUpdater.newUpdater(
    classOf[Dispatcher],
    classOf[LazyExecutorServiceDelegate],
    "executorServiceDelegate")

  /**
   * INTERNAL API
   */
  protected[pekko] def shutdown(): Unit = {
    val newDelegate = executorServiceDelegate.copy() // Doesn't matter which one we copy
    val es = esUpdater.getAndSet(this, newDelegate)
    es.shutdown()
  }

  /**
   * Returns if it was registered
   *
   * INTERNAL API
   */
  protected[pekko] override def registerForExecution(
      mbox: Mailbox,
      hasMessageHint: Boolean,
      hasSystemMessageHint: Boolean): Boolean = {
    if (mbox.canBeScheduledForExecution(hasMessageHint, hasSystemMessageHint)) { // This needs to be here to ensure thread safety and no races
      if (mbox.setAsScheduled()) {
        try {
          executorService.execute(mbox)
          true
        } catch {
          case _: RejectedExecutionException =>
            try {
              executorService.execute(mbox)
              true
            } catch { // Retry once
              case e: RejectedExecutionException =>
                mbox.setAsIdle()
                eventStream.publish(Error(e, getClass.getName, getClass, "registerForExecution was rejected twice!"))
                throw e
            }
        }
      } else false
    } else false
  }

  override val toString: String = Logging.simpleName(this) + "[" + id + "]"
}

object PriorityGenerator {

  /**
   * Creates a PriorityGenerator that uses the supplied function as priority generator
   */
  def apply(priorityFunction: Any => Int): PriorityGenerator = new PriorityGenerator {
    def gen(message: Any): Int = priorityFunction(message)
  }
}

/**
 * A PriorityGenerator is a convenience API to create a Comparator that orders the messages of a
 * PriorityDispatcher
 */
abstract class PriorityGenerator extends java.util.Comparator[Envelope] {
  def gen(message: Any): Int

  final def compare(thisMessage: Envelope, thatMessage: Envelope): Int =
    gen(thisMessage.message) - gen(thatMessage.message)
}

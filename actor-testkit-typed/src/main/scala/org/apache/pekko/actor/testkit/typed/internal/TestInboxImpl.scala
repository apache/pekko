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

package org.apache.pekko.actor.testkit.typed.internal

import java.util.concurrent.ConcurrentLinkedQueue

import scala.annotation.tailrec
import scala.collection.immutable

import org.apache.pekko
import pekko.actor.ActorPath
import pekko.actor.typed.ActorRef
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class TestInboxImpl[T](path: ActorPath)
    extends pekko.actor.testkit.typed.javadsl.TestInbox[T]
    with pekko.actor.testkit.typed.scaladsl.TestInbox[T] {

  private val q = new ConcurrentLinkedQueue[T]

  override val ref: ActorRef[T] = new FunctionRef[T](path, (message, _) => q.add(message))
  override def getRef() = ref

  override def receiveMessage(): T = q.poll() match {
    case null => throw new NoSuchElementException(s"polling on an empty inbox: $path")
    case x    => x
  }

  override def expectMessage(expectedMessage: T): TestInboxImpl[T] = {
    q.poll() match {
      case null    => assert(assertion = false, s"expected message: $expectedMessage but no messages were received")
      case message => assert(message == expectedMessage, s"expected: $expectedMessage but received $message")
    }
    this
  }

  override protected def internalReceiveAll(): immutable.Seq[T] = {
    @tailrec def rec(acc: List[T]): List[T] = q.poll() match {
      case null => acc.reverse
      case x    => rec(x :: acc)
    }

    rec(Nil)
  }

  def hasMessages: Boolean = q.peek() != null

  @InternalApi private[pekko] def as[U]: TestInboxImpl[U] = this.asInstanceOf[TestInboxImpl[U]]

}

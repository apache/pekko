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
import pekko.actor.{ ActorPath, Address, RootActorPath }
import pekko.actor.typed.ActorRef
import pekko.annotation.InternalApi
import pekko.pattern.StatusReply
import pekko.util.OptionVal

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

/**
 * INTERNAL API
 */
@InternalApi
object TestInboxImpl {
  def apply[T](name: String): TestInboxImpl[T] = {
    new TestInboxImpl(address / name)
  }

  private[pekko] val address = RootActorPath(Address("pekko.actor.typed.inbox", "anonymous"))
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class ReplyInboxImpl[T](private var underlying: OptionVal[TestInboxImpl[T]])
    extends pekko.actor.testkit.typed.javadsl.ReplyInbox[T]
    with pekko.actor.testkit.typed.scaladsl.ReplyInbox[T] {

  def receiveReply(): T =
    underlying match {
      case OptionVal.Some(testInbox) =>
        underlying = OptionVal.None
        testInbox.receiveMessage()

      case _ => throw new AssertionError("Reply was already received")
    }

  def expectReply(expectedReply: T): Unit =
    receiveReply() match {
      case matches if matches == expectedReply => ()
      case doesntMatch                         =>
        throw new AssertionError(s"Expected $expectedReply but received $doesntMatch")
    }

  def expectNoReply(): ReplyInboxImpl[T] =
    underlying match {
      case OptionVal.Some(testInbox) if testInbox.hasMessages =>
        throw new AssertionError(s"Expected no reply, but ${receiveReply()} was received")

      case OptionVal.Some(_) => this

      case _ =>
        // already received the reply, so this expectation shouldn't even be made
        throw new AssertionError("Improper expectation of no reply: reply was already received")
    }

  def hasReply: Boolean =
    underlying match {
      case OptionVal.Some(testInbox) => testInbox.hasMessages
      case _                         => false
    }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class StatusReplyInboxImpl[T](private var underlying: OptionVal[TestInboxImpl[StatusReply[T]]])
    extends pekko.actor.testkit.typed.javadsl.StatusReplyInbox[T]
    with pekko.actor.testkit.typed.scaladsl.StatusReplyInbox[T] {

  def receiveStatusReply(): StatusReply[T] =
    underlying match {
      case OptionVal.Some(testInbox) =>
        underlying = OptionVal.None
        testInbox.receiveMessage()

      case _ => throw new AssertionError("Reply was already received")
    }

  def receiveValue(): T =
    receiveStatusReply() match {
      case StatusReply.Success(v) => v.asInstanceOf[T]
      case err                    => throw new AssertionError(s"Expected a successful reply but received $err")
    }

  def receiveError(): Throwable =
    receiveStatusReply() match {
      case StatusReply.Error(t) => t
      case success              => throw new AssertionError(s"Expected an error reply but received $success")
    }

  def expectValue(expectedValue: T): Unit =
    receiveValue() match {
      case matches if matches == expectedValue => ()
      case doesntMatch                         =>
        throw new AssertionError(s"Expected $expectedValue but received $doesntMatch")
    }

  def expectErrorMessage(errorMessage: String): Unit =
    receiveError() match {
      case matches if matches.getMessage == errorMessage => ()
      case doesntMatch                                   =>
        throw new AssertionError(s"Expected a throwable with message $errorMessage, but got ${doesntMatch.getMessage}")
    }

  def expectNoReply(): StatusReplyInboxImpl[T] =
    underlying match {
      case OptionVal.Some(testInbox) if testInbox.hasMessages =>
        throw new AssertionError(s"Expected no reply, but ${receiveStatusReply()} was received")

      case OptionVal.Some(_) => this

      case _ =>
        // already received the reply, so this expectation shouldn't even be made
        throw new AssertionError("Improper expectation of no reply: reply was already received")
    }

  def hasReply: Boolean =
    underlying match {
      case OptionVal.Some(testInbox) => testInbox.hasMessages
      case _                         => false
    }
}

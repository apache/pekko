/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.internal

import java.util.concurrent.ConcurrentLinkedQueue

import scala.annotation.tailrec

import org.apache.pekko
import pekko.{ actor => classic }
import pekko.actor.ActorRefProvider
import pekko.actor.typed.ActorRef
import pekko.actor.typed.internal.{ ActorRefImpl, SystemMessage }
import pekko.actor.typed.internal.InternalRecipientRef
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class DebugRef[T](override val path: classic.ActorPath, override val isLocal: Boolean)
    extends ActorRef[T]
    with ActorRefImpl[T]
    with InternalRecipientRef[T] {

  private val q = new ConcurrentLinkedQueue[Either[SystemMessage, T]]

  override def tell(message: T): Unit = q.add(Right(message))
  override def sendSystem(signal: SystemMessage): Unit = q.add(Left(signal))

  def hasMessage: Boolean = q.peek match {
    case null     => false
    case Left(_)  => false
    case Right(_) => true
  }

  def hasSignal: Boolean = q.peek match {
    case null     => false
    case Left(_)  => true
    case Right(_) => false
  }

  def hasSomething: Boolean = q.peek != null

  def receiveMessage(): T = q.poll match {
    case null           => throw new NoSuchElementException("empty DebugRef")
    case Left(signal)   => throw new IllegalStateException(s"expected message but found signal $signal")
    case Right(message) => message
  }

  def receiveSignal(): SystemMessage = q.poll match {
    case null           => throw new NoSuchElementException("empty DebugRef")
    case Left(signal)   => signal
    case Right(message) => throw new IllegalStateException(s"expected signal but found message $message")
  }

  def receiveAll(): List[Either[SystemMessage, T]] = {
    @tailrec def rec(acc: List[Either[SystemMessage, T]]): List[Either[SystemMessage, T]] =
      q.poll match {
        case null  => acc.reverse
        case other => rec(other :: acc)
      }
    rec(Nil)
  }

  // impl InternalRecipientRef, ask not supported
  override def provider: ActorRefProvider = throw new UnsupportedOperationException("no provider")
  // impl InternalRecipientRef
  def isTerminated: Boolean = false
}

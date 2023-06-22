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

package org.apache.pekko.actor.testkit.typed.javadsl

import java.util.concurrent.ThreadLocalRandom

import scala.collection.immutable

import org.apache.pekko
import pekko.actor.testkit.typed.internal.TestInboxImpl
import pekko.actor.typed.ActorRef
import pekko.annotation.DoNotInherit
import pekko.util.ccompat.JavaConverters._

object TestInbox {
  import pekko.actor.testkit.typed.scaladsl.TestInbox.address

  def create[T](name: String): TestInbox[T] = {
    val uid = ThreadLocalRandom.current().nextInt()
    new TestInboxImpl((address / name).withUid(uid))
  }
  def create[T](): TestInbox[T] = {
    val uid = ThreadLocalRandom.current().nextInt()
    new TestInboxImpl((address / "inbox").withUid(uid))
  }
}

/**
 * Utility for use as an [[ActorRef]] when *synchronously* testing [[pekko.actor.typed.Behavior]]
 * with [[pekko.actor.testkit.typed.javadsl.BehaviorTestKit]].
 *
 * If you plan to use a real [[pekko.actor.typed.ActorSystem]] then use [[pekko.actor.testkit.typed.javadsl.TestProbe]]
 * for asynchronous testing.
 *
 * Use `TestInbox.create` factory methods to create instances
 *
 * Not for user extension
 */
@DoNotInherit
abstract class TestInbox[T] {

  /**
   * The actor ref of the inbox
   */
  def getRef(): ActorRef[T]

  /**
   * Get and remove the oldest message
   */
  def receiveMessage(): T

  /**
   * Assert and remove the the oldest message.
   */
  def expectMessage(expectedMessage: T): TestInbox[T]

  /**
   * Collect all messages in the inbox and clear it out
   */
  def getAllReceived(): java.util.List[T] = internalReceiveAll().asJava

  protected def internalReceiveAll(): immutable.Seq[T]

  def hasMessages: Boolean

  // TODO expectNoMsg etc
}

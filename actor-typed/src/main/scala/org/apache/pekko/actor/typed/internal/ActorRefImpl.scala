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

package org.apache.pekko.actor.typed
package internal

import scala.annotation.unchecked.uncheckedVariance

/**
 * Every ActorRef is also an ActorRefImpl, but these two methods shall be
 * completely hidden from client code. There is an implicit converter
 * available in the package object, enabling `ref.toImpl` (or `ref.toImplN`
 * for `ActorRef[Nothing]`—Scala refuses to infer `Nothing` as a type parameter).
 */
private[pekko] trait ActorRefImpl[-T] extends ActorRef[T] { this: InternalRecipientRef[T] =>
  def sendSystem(signal: SystemMessage): Unit
  def isLocal: Boolean

  final override def narrow[U <: T]: ActorRef[U] = this

  final override def unsafeUpcast[U >: T @uncheckedVariance]: ActorRef[U] = this.asInstanceOf[ActorRef[U]]

  /**
   * Comparison takes path and the unique id of the actor cell into account.
   */
  final override def compareTo(other: ActorRef[?]) = {
    val x = this.path.compareTo(other.path)
    if (x == 0) if (this.path.uid < other.path.uid) -1 else if (this.path.uid == other.path.uid) 0 else 1
    else x
  }

  final override def hashCode: Int = path.uid

  /**
   * Equals takes path and the unique id of the actor cell into account.
   */
  final override def equals(that: Any): Boolean = that match {
    case other: ActorRef[?] => path.uid == other.path.uid && path == other.path
    case _                  => false
  }

  override def toString: String = s"Actor[$path#${path.uid}]"
}

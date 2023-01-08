/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed

import org.apache.pekko.annotation.DoNotInherit

/**
 * This trait is not meant to be extended by user code. If you do so, you may
 * lose binary compatibility.
 *
 * Not for user extension.
 */
@DoNotInherit
trait TypedActorContext[T] {
  // this should be a pure interface, i.e. only abstract methods

  /**
   * Get the `javadsl` of this `ActorContext`.
   */
  def asJava: javadsl.ActorContext[T]

  /**
   * Get the `scaladsl` of this `ActorContext`.
   */
  def asScala: scaladsl.ActorContext[T]
}

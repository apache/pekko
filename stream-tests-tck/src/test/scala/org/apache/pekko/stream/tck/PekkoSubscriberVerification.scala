/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import org.reactivestreams.tck.SubscriberBlackboxVerification
import org.reactivestreams.tck.SubscriberWhiteboxVerification
import org.reactivestreams.tck.TestEnvironment
import org.scalatestplus.testng.TestNGSuiteLike

import org.apache.pekko.actor.ActorSystem

abstract class PekkoSubscriberBlackboxVerification[T](env: TestEnvironment)
    extends SubscriberBlackboxVerification[T](env)
    with TestNGSuiteLike
    with PekkoSubscriberVerificationLike
    with ActorSystemLifecycle {

  def this(printlnDebug: Boolean) =
    this(new TestEnvironment(Timeouts.defaultTimeoutMillis, Timeouts.defaultNoSignalsTimeoutMillis, printlnDebug))

  def this() = this(false)
}

abstract class PekkoSubscriberWhiteboxVerification[T](env: TestEnvironment)
    extends SubscriberWhiteboxVerification[T](env)
    with TestNGSuiteLike
    with PekkoSubscriberVerificationLike {

  def this(printlnDebug: Boolean) =
    this(new TestEnvironment(Timeouts.defaultTimeoutMillis, Timeouts.defaultNoSignalsTimeoutMillis, printlnDebug))

  def this() = this(false)
}

trait PekkoSubscriberVerificationLike {
  implicit def system: ActorSystem
}

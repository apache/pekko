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

import scala.collection.immutable

import org.scalatestplus.testng.TestNGSuiteLike

import org.apache.pekko.stream.testkit.TestPublisher

import org.reactivestreams.Publisher
import org.reactivestreams.tck.PublisherVerification
import org.reactivestreams.tck.TestEnvironment

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

abstract class PekkoPublisherVerification[T](val env: TestEnvironment, publisherShutdownTimeout: Long)
    extends PublisherVerification[T](env, publisherShutdownTimeout)
    with TestNGSuiteLike
    with ActorSystemLifecycle {

  override def additionalConfig: Config =
    ConfigFactory.parseString("""
      pekko.stream.materializer.initial-input-buffer-size = 512
      pekko.stream.materializer.max-input-buffer-size = 512
    """)

  def this(printlnDebug: Boolean) =
    this(
      new TestEnvironment(Timeouts.defaultTimeoutMillis, Timeouts.defaultNoSignalsTimeoutMillis, printlnDebug),
      Timeouts.publisherShutdownTimeoutMillis)

  def this() = this(false)

  override def createFailedPublisher(): Publisher[T] =
    TestPublisher.error(new Exception("Unable to serve subscribers right now!"))

  def iterable(elements: Long): immutable.Iterable[Int] =
    if (elements > Int.MaxValue)
      new immutable.Iterable[Int] { override def iterator = Iterator.from(0) }
    else
      0 until elements.toInt
}

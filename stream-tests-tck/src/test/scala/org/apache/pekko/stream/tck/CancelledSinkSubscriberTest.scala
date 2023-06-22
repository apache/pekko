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

import org.reactivestreams.Subscriber
import org.testng.SkipException

import org.apache.pekko.stream.scaladsl._

class CancelledSinkSubscriberTest extends PekkoSubscriberBlackboxVerification[Int] {

  override def createSubscriber(): Subscriber[Int] =
    Flow[Int].to(Sink.cancelled).runWith(Source.asSubscriber)

  override def createElement(element: Int): Int = element

  override def required_spec201_blackbox_mustSignalDemandViaSubscriptionRequest() = {
    throw new SkipException("Cancelled sink doesn't signal demand")
  }
  override def required_spec209_blackbox_mustBePreparedToReceiveAnOnCompleteSignalWithPrecedingRequestCall() = {
    throw new SkipException("Cancelled sink doesn't signal demand")
  }
  override def required_spec210_blackbox_mustBePreparedToReceiveAnOnErrorSignalWithPrecedingRequestCall() = {
    throw new SkipException("Cancelled sink doesn't signal demand")
  }
}

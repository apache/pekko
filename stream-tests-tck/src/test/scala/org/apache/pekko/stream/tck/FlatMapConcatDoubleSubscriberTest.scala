/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._

import org.reactivestreams.{ Publisher, Subscriber }

import org.apache.pekko.stream.scaladsl.{ Sink, Source }

class FlatMapConcatDoubleSubscriberTest extends PekkoSubscriberBlackboxVerification[Int] {

  def createSubscriber(): Subscriber[Int] = {
    val subscriber = Promise[Subscriber[Int]]()
    Source
      .single(Source.fromPublisher(new Publisher[Int] {
        def subscribe(s: Subscriber[_ >: Int]): Unit =
          subscriber.success(s.asInstanceOf[Subscriber[Int]])
      }))
      .flatMapConcat(identity)
      .runWith(Sink.ignore)

    Await.result(subscriber.future, 1.second)
  }

  def createElement(element: Int): Int = element
}

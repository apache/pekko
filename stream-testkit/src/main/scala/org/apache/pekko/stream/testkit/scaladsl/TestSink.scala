/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.stream._
import pekko.stream.Attributes.none
import pekko.stream.scaladsl._
import pekko.stream.testkit._
import pekko.stream.testkit.StreamTestKit.ProbeSink
import pekko.stream.testkit.TestSubscriber.Probe

/**
 * Factory methods for test sinks.
 */
object TestSink {

  /**
   * A Sink that materialized to a [[pekko.stream.testkit.TestSubscriber.Probe]].
   */
  def probe[T](implicit system: ActorSystem): Sink[T, Probe[T]] =
    Sink.fromGraph[T, TestSubscriber.Probe[T]](new ProbeSink(none, SinkShape(Inlet("ProbeSink.in"))))

  /**
   * A Sink that materialized to a [[pekko.stream.testkit.TestSubscriber.Probe]].
   */
  def apply[T]()(implicit system: ClassicActorSystemProvider): Sink[T, Probe[T]] =
    probe(system.classicSystem)

}

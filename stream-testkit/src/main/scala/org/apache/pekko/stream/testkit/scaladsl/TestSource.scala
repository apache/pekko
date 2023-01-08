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

package org.apache.pekko.stream.testkit.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.stream._
import pekko.stream.Attributes.none
import pekko.stream.scaladsl._
import pekko.stream.testkit._
import pekko.stream.testkit.StreamTestKit.ProbeSource

/**
 * Factory methods for test sources.
 */
object TestSource {

  /**
   * A Source that materializes to a [[pekko.stream.testkit.TestPublisher.Probe]].
   */
  def probe[T](implicit system: ActorSystem): Source[T, TestPublisher.Probe[T]] =
    Source.fromGraph[T, TestPublisher.Probe[T]](new ProbeSource(none, SourceShape(Outlet("ProbeSource.out"))))

  /**
   * A Source that materializes to a [[pekko.stream.testkit.TestPublisher.Probe]].
   */
  def apply[T]()(implicit system: ClassicActorSystemProvider): Source[T, TestPublisher.Probe[T]] =
    probe(system.classicSystem)

}

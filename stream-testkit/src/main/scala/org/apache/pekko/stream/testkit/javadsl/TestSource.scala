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

package org.apache.pekko.stream.testkit.javadsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.stream.javadsl.Source
import pekko.stream.testkit._

/** Java API */
object TestSource {

  /**
   * A Source that materializes to a [[pekko.stream.testkit.TestPublisher.Probe]].
   */
  def probe[T](system: ActorSystem): Source[T, TestPublisher.Probe[T]] =
    new Source(scaladsl.TestSource.probe[T](system))

  /**
   * A Source that materializes to a [[pekko.stream.testkit.TestPublisher.Probe]].
   */
  def create[T](system: ClassicActorSystemProvider): Source[T, TestPublisher.Probe[T]] =
    probe(system.classicSystem)

}

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit.javadsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.stream.javadsl.Sink
import pekko.stream.testkit._

/** Java API */
object TestSink {

  /**
   * A Sink that materialized to a [[pekko.stream.testkit.TestSubscriber.Probe]].
   */
  def probe[T](system: ActorSystem): Sink[T, TestSubscriber.Probe[T]] =
    new Sink(scaladsl.TestSink.probe[T](system))

  /**
   * A Sink that materialized to a [[pekko.stream.testkit.TestSubscriber.Probe]].
   */
  def create[T](system: ClassicActorSystemProvider): Sink[T, TestSubscriber.Probe[T]] =
    probe(system.classicSystem)

}

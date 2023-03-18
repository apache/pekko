/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import SubstreamCancelStrategies._

import scala.annotation.nowarn

/**
 * Represents a strategy that decides how to deal with substream events.
 */
@deprecated(
  "Use .withAttributes(ActorAttributes.supervisionStrategy(equivalentDecider)) rather than a SubstreamCancelStrategy",
  since = "1.1.0")
sealed abstract class SubstreamCancelStrategy

private[pekko] object SubstreamCancelStrategies {

  /**
   * INTERNAL API
   */
  @nowarn("msg=deprecated")
  private[pekko] case object Propagate extends SubstreamCancelStrategy

  /**
   * INTERNAL API
   */
  @nowarn("msg=deprecated")
  private[pekko] case object Drain extends SubstreamCancelStrategy
}

@deprecated(
  "Use .withAttributes(ActorAttributes.supervisionStrategy(equivalentDecider)) rather than a SubstreamCancelStrategy",
  since = "1.1.0")
object SubstreamCancelStrategy {

  /**
   * Cancel the stream of streams if any substream is cancelled.
   */
  def propagate: SubstreamCancelStrategy = Propagate

  /**
   * Drain substream on cancellation in order to prevent stalling of the stream of streams.
   */
  def drain: SubstreamCancelStrategy = Drain
}

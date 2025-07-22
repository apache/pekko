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

package org.apache.pekko.stream.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.japi.function.Creator
import pekko.stream.RestartSettings

/**
 * A RestartSource wraps a [[Source]] that gets restarted when it completes or fails.
 *
 * They are useful for graphs that need to run for longer than the [[Source]] can necessarily guarantee it will, for
 * example, for [[Source]] streams that depend on a remote server that may crash or become partitioned. The
 * RestartSource ensures that the graph can continue running while the [[Source]] restarts.
 */
object RestartSource {

  /**
   * Wrap the given [[Source]] with a [[Source]] that will restart it when it fails or complete using an exponential
   * backoff.
   *
   * This [[Source]] will not emit a complete or failure as long as maxRestarts is not reached, since the completion
   * or failure of the wrapped [[Source]] is handled by restarting it. The wrapped [[Source]] can however be cancelled
   * by cancelling this [[Source]]. When that happens, the wrapped [[Source]], if currently running will be cancelled,
   * and it will not be restarted.
   * This can be triggered simply by the downstream cancelling, or externally by introducing a [[KillSwitch]] right
   * after this [[Source]] in the graph.
   *
   * This uses the same exponential backoff algorithm as [[pekko.pattern.BackoffOpts]].
   *
   * @param settings [[RestartSettings]] defining restart configuration
   * @param sourceFactory A factory for producing the [[Source]] to wrap.
   */
  def withBackoff[T](settings: RestartSettings, sourceFactory: Creator[Source[T, _]]): Source[T, NotUsed] =
    pekko.stream.scaladsl.RestartSource
      .withBackoff(settings) { () =>
        sourceFactory.create().asScala
      }
      .asJava

  /**
   * Wrap the given [[Source]] with a [[Source]] that will restart it when it fails using an exponential backoff.
   *
   * This [[Source]] will not emit a failure as long as maxRestarts is not reached, since failure of the wrapped [[Source]]
   * is handled by restarting it. The wrapped [[Source]] can be cancelled
   * by cancelling this [[Source]]. When that happens, the wrapped [[Source]], if currently running will be cancelled,
   * and it will not be restarted. This can be triggered simply by the downstream cancelling, or externally by
   * introducing a [[KillSwitch]] right after this [[Source]] in the graph.
   *
   * This uses the same exponential backoff algorithm as [[pekko.pattern.BackoffOpts]].
   *
   * @param settings [[RestartSettings]] defining restart configuration
   * @param sourceFactory A factory for producing the [[Source]] to wrap.
   */
  def onFailuresWithBackoff[T](settings: RestartSettings, sourceFactory: Creator[Source[T, _]]): Source[T, NotUsed] =
    pekko.stream.scaladsl.RestartSource
      .onFailuresWithBackoff(settings) { () =>
        sourceFactory.create().asScala
      }
      .asJava
}

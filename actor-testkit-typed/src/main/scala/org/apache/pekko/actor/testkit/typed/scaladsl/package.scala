/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed

import scala.concurrent.duration.FiniteDuration

package object scaladsl {

  /**
   * Scala API. Scale timeouts (durations) during tests with the configured
   * 'pekko.actor.testkit.typed.timefactor'.
   * Implicit class providing `dilated` method.
   *
   * {{{
   * import scala.concurrent.duration._
   * import org.apache.pekko.actor.testkit.typed.scaladsl._
   * 10.milliseconds.dilated
   * }}}
   *
   * Uses the scaling factor from the `TestTimeFactor` in the [[TestKitSettings]]
   * (in implicit scope).
   */
  implicit class TestDuration(val duration: FiniteDuration) extends AnyVal {
    def dilated(implicit settings: TestKitSettings): FiniteDuration = settings.dilated(duration)
  }

}

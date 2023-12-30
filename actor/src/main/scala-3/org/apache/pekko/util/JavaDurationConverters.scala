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

package org.apache.pekko.util
import java.time.{ Duration => JDuration }

import scala.concurrent.duration.{ Duration, FiniteDuration }

import org.apache.pekko.annotation.InternalStableApi

/**
 * INTERNAL API
 */
@InternalStableApi
private[pekko] object JavaDurationConverters {

  // Ideally this should have the Scala 3 inline keyword but then Java sources are
  // unable to call this method, see https://github.com/lampepfl/dotty/issues/19346
  def asFiniteDuration(duration: JDuration): FiniteDuration = duration.asScala

  final implicit class JavaDurationOps(val self: JDuration) extends AnyVal {
    inline def asScala: FiniteDuration = Duration.fromNanos(self.toNanos)
  }

  final implicit class ScalaDurationOps(val self: Duration) extends AnyVal {
    inline def asJava: JDuration = JDuration.ofNanos(self.toNanos)
  }
}

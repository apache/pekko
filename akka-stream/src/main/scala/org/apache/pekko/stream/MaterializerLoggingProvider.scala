/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.event.LoggingAdapter

/**
 * Not for user extension
 */
@DoNotInherit
trait MaterializerLoggingProvider { this: Materializer =>

  def makeLogger(logSource: Class[Any]): LoggingAdapter

}

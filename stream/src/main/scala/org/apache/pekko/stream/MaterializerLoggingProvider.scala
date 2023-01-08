/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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

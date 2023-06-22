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

package org.apache.pekko.stream

import scala.util.control.NoStackTrace

/**
 * This exception signals that materialized value is already detached from stream. This usually happens
 * when stream is completed and an ActorSystem is shut down while materialized object is still available.
 */
final class StreamDetachedException(message: String) extends RuntimeException(message) with NoStackTrace {

  def this() = this("Stream is terminated. Materialized value is detached.")
}

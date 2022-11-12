/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed

import scala.util.control.NoStackTrace

/**
 * A predefined exception that can be used in tests. It doesn't include a stack trace.
 */
final case class TestException(message: String) extends RuntimeException(message) with NoStackTrace

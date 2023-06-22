/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import scala.util.control.NoStackTrace

/**
 * A predefined exception that can be used in tests. It doesn't include a stack trace.
 */
final case class TestException(message: String) extends RuntimeException(message) with NoStackTrace

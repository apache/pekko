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

package org.apache.pekko.stream.scaladsl

object TestConfig {
  val numberOfTestsToRun = System.getProperty("pekko.stream.test.numberOfRandomizedTests", "10").toInt
  val RandomTestRange = 1 to numberOfTestsToRun
}

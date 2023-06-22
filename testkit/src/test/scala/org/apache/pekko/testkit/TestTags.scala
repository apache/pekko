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

package org.apache.pekko.testkit

import org.scalatest.Tag

object TimingTest extends Tag("timing")
object LongRunningTest extends Tag("long-running")
object PerformanceTest extends Tag("performance")

object GHExcludeTest extends Tag("gh-exclude")
object GHExcludeAeronTest extends Tag("gh-exclude-aeron")

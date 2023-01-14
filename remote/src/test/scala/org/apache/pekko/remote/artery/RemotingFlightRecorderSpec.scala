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

package org.apache.pekko.remote.artery

import org.scalatest.matchers.should.Matchers

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.util.JavaVersion

class RemotingFlightRecorderSpec extends PekkoSpec with Matchers {

  "The RemotingFlightRecorder" must {

    "use the no-op recorder by default when running on JDK 8" in {
      val extension = RemotingFlightRecorder(system)
      if (JavaVersion.majorVersion < 11)
        extension should ===(NoOpRemotingFlightRecorder)
    }
  }

}

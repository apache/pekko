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

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.testkit.PekkoSpec

import org.scalatest.matchers.should.Matchers

class RemotingFlightRecorderSpec extends PekkoSpec with Matchers {

  "The RemotingFlightRecorder" must {

    "not use the no-op recorder by default" in {
      val extension = RemotingFlightRecorder(system)
      extension should !==(NoOpRemotingFlightRecorder)
    }
  }

}

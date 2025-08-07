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

package org.apache.pekko.remote.artery.jfr

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.remote.artery.NoOpRemotingFlightRecorder
import pekko.remote.artery.RemotingFlightRecorder
import pekko.testkit.PekkoSpec
import pekko.testkit.TestKit

class JFRRemotingFlightRecorderSpec extends PekkoSpec {

  "The RemotingFlightRecorder" must {

    "use the JFR one on Java 11" in {
      val extension = RemotingFlightRecorder(system)
      extension shouldBe a[JFRRemotingFlightRecorder]

      extension.transportStopped() // try to actually report something and see that it doesn't throw or something
    }

    "be disabled if configured to" in {
      val system = ActorSystem(
        "JFRRemotingFlightRecorderSpec-2",
        ConfigFactory.parseString(
          """
           pekko.java-flight-recorder.enabled = false
            """))
      try {
        val extension = RemotingFlightRecorder(system)
        extension should ===(NoOpRemotingFlightRecorder)
      } finally {
        TestKit.shutdownActorSystem(system)
      }
    }
  }

}

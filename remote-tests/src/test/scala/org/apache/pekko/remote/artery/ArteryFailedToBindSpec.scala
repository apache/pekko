/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.remote.RARP
import pekko.remote.RemoteTransportException
import pekko.testkit.SocketUtil
import pekko.testkit.TestKit

class ArteryFailedToBindSpec extends AnyWordSpec with Matchers {

  "an ActorSystem" must {
    "not start if port is taken" in {

      // this test is tweaked in Jenkins CI by passing -Dpekko.remote.artery.transport
      // therefore we must decide whether to use UDP or not based on the runtime config
      val arterySettings = ArterySettings(ConfigFactory.load().getConfig("pekko.remote.artery"))
      val useUdp = arterySettings.Transport == ArterySettings.AeronUpd
      val port = SocketUtil.temporaryLocalPort(useUdp)

      val config = ConfigFactory.parseString(s"""
           |pekko {
           |  actor {
           |    provider = remote
           |  }
           |  remote {
           |    artery {
           |      enabled = on
           |      canonical.hostname = "127.0.0.1"
           |      canonical.port = $port
           |      aeron.log-aeron-counters = on
           |    }
           |  }
           |}
       """.stripMargin)
      val as = ActorSystem("BindTest1", config)
      try {
        val ex = intercept[RemoteTransportException] {
          ActorSystem("BindTest2", config)
        }
        RARP(as).provider.transport.asInstanceOf[ArteryTransport].settings.Transport match {
          case ArterySettings.AeronUpd =>
            ex.getMessage should ===("Inbound Aeron channel is in errored state. See Aeron logs for details.")
          case ArterySettings.Tcp | ArterySettings.TlsTcp =>
            ex.getMessage should startWith("Failed to bind TCP")
        }

      } finally
        TestKit.shutdownActorSystem(as)
    }
  }
}

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

package org.apache.pekko.remote.classic

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.SocketUtil
import org.jboss.netty.channel.ChannelException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RemotingNetty4FailedToBindSpec extends AnyWordSpec with Matchers {

  "an ActorSystem" must {
    "not start if port is taken (Netty 4)" in {
      val port = SocketUtil.temporaryLocalPort()
      val config = ConfigFactory.parseString(s"""
           |pekko {
           |  actor {
           |    provider = remote
           |  }
           |  remote.artery.enabled = off
           |  remote.classic.netty {
           |    ssl {
           |      ssl-engine-provider = org.apache.pekko.remote.transport.netty4.ConfigSSLEngineProvider
           |    }
           |    tcp {
           |      hostname = "127.0.0.1"
           |      port = $port
           |      transport-class = "org.apache.pekko.remote.transport.netty4.NettyTransport"
           |    }
           |  }
           |}
       """.stripMargin)
      val as = ActorSystem("RemotingFailedToBindSpec", config)
      try {
        val ex = intercept[ChannelException] {
          ActorSystem("BindTest2", config)
        }
        ex.getMessage should startWith("Failed to bind")
      } finally {
        as.terminate()
      }
    }
  }
}

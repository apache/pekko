/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ ActorIdentity, Identify }
import pekko.actor.RootActorPath
import pekko.testkit._

object HandshakeDenySpec {

  val commonConfig = ConfigFactory.parseString(s"""
     pekko.loglevel = WARNING
     pekko.remote.artery.advanced.handshake-timeout = 2s
     pekko.remote.artery.advanced.aeron.image-liveness-timeout = 1.9s
  """).withFallback(ArterySpecSupport.defaultConfig)

}

class HandshakeDenySpec extends ArteryMultiNodeSpec(HandshakeDenySpec.commonConfig) with ImplicitSender {

  var systemB = newRemoteSystem(name = Some("systemB"))

  "Artery handshake" must {

    "be denied when originating address is unknown" in {
      val sel = system.actorSelection(RootActorPath(address(systemB).copy(host = Some("127.0.0.1"))) / "user" / "echo")

      systemB.actorOf(TestActors.echoActorProps, "echo")

      EventFilter
        .warning(start = "Dropping Handshake Request from")
        .intercept {
          sel ! Identify("hi echo")
          // handshake timeout and Identify message in SendQueue is sent to deadLetters,
          // which generates the ActorIdentity(None)
          expectMsg(5.seconds, ActorIdentity("hi echo", None))
        }(systemB)
    }

  }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ ActorIdentity, Identify }
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

object HandshakeFailureSpec {

  val commonConfig = ConfigFactory.parseString(s"""
     pekko.remote.artery.advanced.handshake-timeout = 2s
     pekko.remote.artery.advanced.aeron.image-liveness-timeout = 1.9s
  """).withFallback(ArterySpecSupport.defaultConfig)

}

class HandshakeFailureSpec extends ArteryMultiNodeSpec(HandshakeFailureSpec.commonConfig) with ImplicitSender {

  val portB = freePort()

  "Artery handshake" must {

    "allow for timeout and later connect" in {
      def sel = system.actorSelection(s"pekko://systemB@localhost:$portB/user/echo")
      sel ! "hello"
      expectNoMessage(3.seconds) // longer than handshake-timeout

      val systemB =
        newRemoteSystem(name = Some("systemB"), extraConfig = Some(s"pekko.remote.artery.canonical.port = $portB"))
      systemB.actorOf(TestActors.echoActorProps, "echo")

      within(10.seconds) {
        awaitAssert {
          val probe = TestProbe()
          sel.tell("hello2", probe.ref)
          probe.expectMsg(1.second, "hello2")
        }
      }

      sel ! Identify(None)
      val remoteRef = expectMsgType[ActorIdentity].ref.get

      remoteRef ! "ping"
      expectMsg("ping")
    }

  }

}

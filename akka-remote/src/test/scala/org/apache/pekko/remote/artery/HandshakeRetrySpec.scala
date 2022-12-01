/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor._
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors

object HandshakeRetrySpec {
  val commonConfig = ConfigFactory.parseString(s"""
     pekko.remote.artery.advanced.handshake-timeout = 10s
     pekko.remote.artery.advanced.aeron.image-liveness-timeout = 7s
  """).withFallback(ArterySpecSupport.defaultConfig)

}

class HandshakeRetrySpec extends ArteryMultiNodeSpec(HandshakeRetrySpec.commonConfig) with ImplicitSender {

  val portB = freePort()

  "Artery handshake" must {

    "be retried during handshake-timeout (no message loss)" in {
      def sel = system.actorSelection(s"akka://systemB@localhost:$portB/user/echo")
      sel ! "hello"
      expectNoMessage(1.second)

      val systemB =
        newRemoteSystem(name = Some("systemB"), extraConfig = Some(s"pekko.remote.artery.canonical.port = $portB"))
      systemB.actorOf(TestActors.echoActorProps, "echo")

      expectMsg("hello")

      sel ! Identify(None)
      val remoteRef = expectMsgType[ActorIdentity].ref.get

      remoteRef ! "ping"
      expectMsg("ping")
    }

  }

}

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.{ ActorIdentity, Identify, RootActorPath }
import pekko.testkit.EventFilter
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors

object SerializationErrorSpec {

  class NotSerializableMsg

}

class SerializationErrorSpec extends ArteryMultiNodeSpec(ArterySpecSupport.defaultConfig) with ImplicitSender {
  import SerializationErrorSpec._

  val systemB = newRemoteSystem(
    name = Some("systemB"),
    extraConfig = Some("""
       akka.actor.serialization-identifiers {
         # this will cause deserialization error
         "org.apache.pekko.serialization.ByteArraySerializer" = -4
       }
       """))
  systemB.actorOf(TestActors.echoActorProps, "echo")
  val addressB = address(systemB)
  val rootB = RootActorPath(addressB)

  "Serialization error" must {

    "be logged when serialize fails" in {
      val remoteRef = {
        system.actorSelection(rootB / "user" / "echo") ! Identify(None)
        expectMsgType[ActorIdentity].ref.get
      }

      remoteRef ! "ping"
      expectMsg("ping")

      EventFilter[java.io.NotSerializableException](start = "Failed to serialize message", occurrences = 1).intercept {
        remoteRef ! new NotSerializableMsg()
      }

      remoteRef ! "ping2"
      expectMsg("ping2")
    }

    "be logged when deserialize fails" in {
      val remoteRef = {
        system.actorSelection(rootB / "user" / "echo") ! Identify(None)
        expectMsgType[ActorIdentity].ref.get
      }

      remoteRef ! "ping"
      expectMsg("ping")

      EventFilter
        .error(pattern = """Failed to deserialize message from \[.*\] with serializer id \[4\]""", occurrences = 1)
        .intercept {
          remoteRef ! "boom".getBytes("utf-8")
        }(systemB)

      remoteRef ! "ping2"
      expectMsg("ping2")
    }

  }

}

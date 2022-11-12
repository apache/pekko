/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.serialization

import org.apache.pekko
import pekko.actor.ActorSelectionMessage
import pekko.actor.SelectChildName
import pekko.actor.SelectChildPattern
import pekko.actor.SelectParent
import pekko.remote.DaemonMsgCreate
import pekko.serialization.SerializationExtension
import pekko.testkit.AkkaSpec
import pekko.testkit.TestActors

class MessageContainerSerializerSpec extends AkkaSpec {

  val ser = SerializationExtension(system)

  "DaemonMsgCreateSerializer" must {

    "resolve serializer for ActorSelectionMessage" in {
      ser.serializerFor(classOf[ActorSelectionMessage]).getClass should ===(classOf[MessageContainerSerializer])
    }

    "serialize and de-serialize ActorSelectionMessage" in {
      verifySerialization(
        ActorSelectionMessage(
          "hello",
          Vector(
            SelectChildName("user"),
            SelectChildName("a"),
            SelectChildName("b"),
            SelectParent,
            SelectChildPattern("*"),
            SelectChildName("c")),
          wildcardFanOut = true))
    }

    "serialize and deserialize DaemonMsgCreate with tagged actor" in {
      val props = TestActors.echoActorProps.withActorTags("one", "two")
      val deserialized =
        verifySerialization(DaemonMsgCreate(props, props.deploy, "/user/some/path", system.deadLetters))
      deserialized.deploy.tags should ===(Set("one", "two"))
    }

    def verifySerialization[T <: AnyRef](msg: T): T = {
      val deserialized = ser.deserialize(ser.serialize(msg).get, msg.getClass).get
      deserialized should ===(msg)
      deserialized
    }

  }
}

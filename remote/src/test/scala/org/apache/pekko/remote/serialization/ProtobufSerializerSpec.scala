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

package org.apache.pekko.remote.serialization

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.remote.MessageSerializer
import pekko.remote.ProtobufProtocol.MyMessage
import pekko.remote.WireFormats.SerializedMessage
import pekko.remote.protobuf.v3.ProtobufProtocolV3.MyMessageV3
import pekko.serialization.SerializationExtension
import pekko.testkit.PekkoSpec
import pekko.util.unused

// those must be defined as top level classes, to have static parseFrom
case class MaliciousMessage() {}

object ProtobufSerializerSpec {
  trait AnotherInterface
  abstract class AnotherBase
}

object AnotherMessage {
  def parseFrom(@unused bytes: Array[Byte]): AnotherMessage =
    new AnotherMessage
}
case class AnotherMessage() {}

object AnotherMessage2 {
  def parseFrom(@unused bytes: Array[Byte]): AnotherMessage2 =
    new AnotherMessage2
}
case class AnotherMessage2() extends ProtobufSerializerSpec.AnotherInterface {}

object AnotherMessage3 {
  def parseFrom(@unused bytes: Array[Byte]): AnotherMessage3 =
    new AnotherMessage3
}
case class AnotherMessage3() extends ProtobufSerializerSpec.AnotherBase {}

object MaliciousMessage {
  def parseFrom(@unused bytes: Array[Byte]): MaliciousMessage =
    new MaliciousMessage
}

class ProtobufSerializerSpec extends PekkoSpec(s"""
  pekko.serialization.protobuf.allowed-classes = [
      "com.google.protobuf.GeneratedMessage",
      "com.google.protobuf.GeneratedMessage",
      "scalapb.GeneratedMessageCompanion",
      "org.apache.pekko.protobufv3.internal.GeneratedMessage",
      "${classOf[AnotherMessage].getName}",
      "${classOf[ProtobufSerializerSpec.AnotherInterface].getName}",
      "${classOf[ProtobufSerializerSpec.AnotherBase].getName}"
    ]
  """) {

  val ser = SerializationExtension(system)

  "Serialization" must {

    "resolve protobuf serializer" in {
      ser.serializerFor(classOf[SerializedMessage]).getClass should ===(classOf[ProtobufSerializer])
      ser.serializerFor(classOf[MyMessage]).getClass should ===(classOf[ProtobufSerializer])
      ser.serializerFor(classOf[MyMessageV3]).getClass should ===(classOf[ProtobufSerializer])
    }

    "work for SerializedMessage (just an org.apache.pekko.protobuf message)" in {
      // create a protobuf message
      val protobufMessage: SerializedMessage =
        MessageSerializer.serialize(system.asInstanceOf[ExtendedActorSystem], "hello")
      // serialize it with ProtobufSerializer
      val bytes = ser.serialize(protobufMessage).get
      // deserialize the bytes with ProtobufSerializer
      val deserialized = ser.deserialize(bytes, protobufMessage.getClass).get.asInstanceOf[SerializedMessage]
      deserialized.getSerializerId should ===(protobufMessage.getSerializerId)
      deserialized.getMessage should ===(protobufMessage.getMessage) // same "hello"
    }

    "work for a serialized protobuf v3 message" in {
      val protobufV3Message: MyMessageV3 =
        MyMessageV3.newBuilder().setQuery("query1").setPageNumber(1).setResultPerPage(2).build()
      val bytes = ser.serialize(protobufV3Message).get
      val deserialized: MyMessageV3 = ser.deserialize(bytes, protobufV3Message.getClass).get
      protobufV3Message should ===(deserialized)
    }

    "disallow deserialization of classes that are not in bindings and not in configured allowed classes" in {
      val originalSerializer = ser.serializerFor(classOf[MyMessage])

      intercept[IllegalArgumentException] {
        ser.deserialize(Array[Byte](), originalSerializer.identifier, classOf[MaliciousMessage].getName).get
      }
    }

    "allow deserialization of classes in configured allowed classes" in {
      val originalSerializer = ser.serializerFor(classOf[MyMessage])

      val deserialized =
        ser.deserialize(Array[Byte](), originalSerializer.identifier, classOf[AnotherMessage].getName).get
      deserialized.getClass should ===(classOf[AnotherMessage])
    }

    "allow deserialization of interfaces in configured allowed classes" in {
      val originalSerializer = ser.serializerFor(classOf[MyMessage])

      val deserialized =
        ser.deserialize(Array[Byte](), originalSerializer.identifier, classOf[AnotherMessage2].getName).get
      deserialized.getClass should ===(classOf[AnotherMessage2])
    }

    "allow deserialization of super classes in configured allowed classes" in {
      val originalSerializer = ser.serializerFor(classOf[MyMessage])

      val deserialized =
        ser.deserialize(Array[Byte](), originalSerializer.identifier, classOf[AnotherMessage3].getName).get
      deserialized.getClass should ===(classOf[AnotherMessage3])
    }

  }
}

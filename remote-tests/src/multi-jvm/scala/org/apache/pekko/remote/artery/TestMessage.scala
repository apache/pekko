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

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.protobufv3.internal.ByteString
import pekko.remote.artery.protobuf.{ TestMessages => proto }
import pekko.serialization.SerializerWithStringManifest

import java.io.NotSerializableException

object TestMessage {
  final case class Item(id: Long, name: String)
}

final case class TestMessage(
    id: Long,
    name: String,
    status: Boolean,
    description: String,
    payload: Array[Byte],
    items: Vector[TestMessage.Item])

class TestMessageSerializer(val system: ExtendedActorSystem) extends SerializerWithStringManifest {

  val TestMessageManifest = "A"

  override val identifier: Int = 101

  override def manifest(o: AnyRef): String =
    o match {
      case _: TestMessage => TestMessageManifest
      case _              => throw new NotSerializableException()
    }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case msg: TestMessage =>
      val builder = proto.TestMessage
        .newBuilder()
        .setId(msg.id)
        .setName(msg.name)
        .setDescription(msg.description)
        .setStatus(msg.status)
        .setPayload(ByteString.copyFrom(msg.payload))
      msg.items.foreach { item =>
        builder.addItems(proto.Item.newBuilder().setId(item.id).setName(item.name))
      }
      builder.build().toByteArray()
    case _ => throw new NotSerializableException()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val protoMsg = proto.TestMessage.parseFrom(bytes)
    import pekko.util.ccompat.JavaConverters._
    val items = protoMsg.getItemsList.asScala.map { item =>
      TestMessage.Item(item.getId, item.getName)
    }.toVector

    TestMessage(
      id = protoMsg.getId,
      name = protoMsg.getName,
      description = protoMsg.getDescription,
      status = protoMsg.getStatus,
      payload = protoMsg.getPayload.toByteArray(),
      items = items)
  }
}

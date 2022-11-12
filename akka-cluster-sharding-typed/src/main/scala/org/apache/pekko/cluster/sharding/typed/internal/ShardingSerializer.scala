/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.internal

import java.io.NotSerializableException
import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.internal.protobuf.ShardingMessages
import pekko.protobufv3.internal.CodedOutputStream
import pekko.remote.serialization.WrappedPayloadSupport
import pekko.serialization.BaseSerializer
import pekko.serialization.ByteBufferSerializer
import pekko.serialization.SerializerWithStringManifest

import java.nio.ByteBuffer

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ShardingSerializer(val system: pekko.actor.ExtendedActorSystem)
    extends SerializerWithStringManifest
    with ByteBufferSerializer
    with BaseSerializer {

  private val payloadSupport = new WrappedPayloadSupport(system)

  private val ShardingEnvelopeManifest = "a"

  override def manifest(o: AnyRef): String = o match {
    case _: ShardingEnvelope[_] => ShardingEnvelopeManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case env: ShardingEnvelope[_] =>
      val builder = ShardingMessages.ShardingEnvelope.newBuilder()
      builder.setEntityId(env.entityId)
      builder.setMessage(payloadSupport.payloadBuilder(env.message))
      builder.build().toByteArray()

    case _ =>
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case ShardingEnvelopeManifest =>
      val env = ShardingMessages.ShardingEnvelope.parseFrom(bytes)
      val entityId = env.getEntityId
      val wrappedMsg = payloadSupport.deserializePayload(env.getMessage)
      ShardingEnvelope(entityId, wrappedMsg)
    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  // buffer based avoiding a copy for artery
  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = o match {
    case env: ShardingEnvelope[_] =>
      val builder = ShardingMessages.ShardingEnvelope.newBuilder()
      builder.setEntityId(env.entityId)
      builder.setMessage(payloadSupport.payloadBuilder(env.message))
      val codedOutputStream = CodedOutputStream.newInstance(buf)
      builder.build().writeTo(codedOutputStream)
      codedOutputStream.flush()
    case _ =>
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = manifest match {
    case ShardingEnvelopeManifest =>
      val env = ShardingMessages.ShardingEnvelope.parseFrom(buf)
      val entityId = env.getEntityId
      val wrappedMsg = payloadSupport.deserializePayload(env.getMessage)
      ShardingEnvelope(entityId, wrappedMsg)
    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.internal

import java.io.NotSerializableException
import java.nio.ByteBuffer
import java.time.Instant

import org.apache.pekko
import pekko.actor.typed.ActorRefResolver
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.ChangeNumberOfProcesses
import pekko.cluster.sharding.typed.GetNumberOfProcesses
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.internal.ShardedDaemonProcessCoordinator.GetNumberOfProcessesReply
import pekko.cluster.sharding.typed.internal.protobuf.ShardingMessages
import pekko.protobufv3.internal.CodedOutputStream
import pekko.remote.serialization.WrappedPayloadSupport
import pekko.serialization.BaseSerializer
import pekko.serialization.ByteBufferSerializer
import pekko.serialization.SerializerWithStringManifest

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ShardingSerializer(val system: pekko.actor.ExtendedActorSystem)
    extends SerializerWithStringManifest
    with ByteBufferSerializer
    with BaseSerializer {

  private val payloadSupport = new WrappedPayloadSupport(system)
  private lazy val resolver = ActorRefResolver(system.toTyped)

  private val ShardingEnvelopeManifest = "a"
  private val DaemonProcessStateManifest = "b"
  private val ChangeNumberOfProcessesManifest = "c"
  private val GetNumberOfProcessesManifest = "d"
  private val GetNumberOfProcessesReplyManifest = "e"

  override def manifest(o: AnyRef): String = o match {
    case _: ShardingEnvelope[_]       => ShardingEnvelopeManifest
    case _: ShardedDaemonProcessState => DaemonProcessStateManifest
    case _: ChangeNumberOfProcesses   => ChangeNumberOfProcessesManifest
    case _: GetNumberOfProcesses      => GetNumberOfProcessesManifest
    case _: GetNumberOfProcessesReply => GetNumberOfProcessesReplyManifest
    case _                            =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case env: ShardingEnvelope[_] =>
      val builder = ShardingMessages.ShardingEnvelope.newBuilder()
      builder.setEntityId(env.entityId)
      builder.setMessage(payloadSupport.payloadBuilder(env.message))
      builder.build().toByteArray()

    case state: ShardedDaemonProcessState =>
      ShardingMessages.DaemonProcessScaleState
        .newBuilder()
        .setRevision(state.revision)
        .setNumberOfProcesses(state.numberOfProcesses)
        .setCompleted(state.completed)
        .setStartedTimestampMillis(state.started.toEpochMilli)
        .build()
        .toByteArray()

    case change: ChangeNumberOfProcesses =>
      ShardingMessages.ChangeNumberOfProcesses
        .newBuilder()
        .setNewNumberOfProcesses(change.newNumberOfProcesses)
        .setReplyTo(resolver.toSerializationFormat(change.replyTo))
        .build()
        .toByteArray()

    case get: GetNumberOfProcesses =>
      ShardingMessages.GetNumberOfProcesses
        .newBuilder()
        .setReplyTo(resolver.toSerializationFormat(get.replyTo))
        .build()
        .toByteArray()

    case reply: GetNumberOfProcessesReply =>
      ShardingMessages.GetNumberOfProcessesReply
        .newBuilder()
        .setRevision(reply.revision)
        .setRescaleInProgress(reply.rescaleInProgress)
        .setNumberOfProcesses(reply.numberOfProcesses)
        .setStartedTimestampMillis(reply.started.toEpochMilli)
        .build()
        .toByteArray()

    case _ =>
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case ShardingEnvelopeManifest =>
      val env = ShardingMessages.ShardingEnvelope.parseFrom(bytes)
      val entityId = env.getEntityId
      val wrappedMsg = payloadSupport.deserializePayload(env.getMessage)
      ShardingEnvelope(entityId, wrappedMsg)

    case DaemonProcessStateManifest =>
      val st = ShardingMessages.DaemonProcessScaleState.parseFrom(bytes)
      ShardedDaemonProcessState(
        st.getRevision,
        st.getNumberOfProcesses,
        st.getCompleted,
        Instant.ofEpochMilli(st.getStartedTimestampMillis))

    case ChangeNumberOfProcessesManifest =>
      val change = ShardingMessages.ChangeNumberOfProcesses.parseFrom(bytes)
      ChangeNumberOfProcesses(change.getNewNumberOfProcesses, resolver.resolveActorRef(change.getReplyTo))

    case GetNumberOfProcessesManifest =>
      val get = ShardingMessages.GetNumberOfProcesses.parseFrom(bytes)
      GetNumberOfProcesses(resolver.resolveActorRef(get.getReplyTo))

    case GetNumberOfProcessesReplyManifest =>
      val reply = ShardingMessages.GetNumberOfProcessesReply.parseFrom(bytes)
      GetNumberOfProcessesReply(
        reply.getNumberOfProcesses,
        Instant.ofEpochMilli(reply.getStartedTimestampMillis),
        reply.getRescaleInProgress,
        reply.getRevision)

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

    case state: ShardedDaemonProcessState =>
      val codedOutputStream = CodedOutputStream.newInstance(buf)
      ShardingMessages.DaemonProcessScaleState
        .newBuilder()
        .setRevision(state.revision)
        .setNumberOfProcesses(state.numberOfProcesses)
        .setCompleted(state.completed)
        .setStartedTimestampMillis(state.started.toEpochMilli)
        .build()
        .writeTo(codedOutputStream)
      codedOutputStream.flush()

    case change: ChangeNumberOfProcesses =>
      val codedOutputStream = CodedOutputStream.newInstance(buf)
      ShardingMessages.ChangeNumberOfProcesses
        .newBuilder()
        .setNewNumberOfProcesses(change.newNumberOfProcesses)
        .setReplyTo(resolver.toSerializationFormat(change.replyTo))
        .build()
        .writeTo(codedOutputStream)
      codedOutputStream.flush()

    case get: GetNumberOfProcesses =>
      val codedOutputStream = CodedOutputStream.newInstance(buf)
      ShardingMessages.GetNumberOfProcesses
        .newBuilder()
        .setReplyTo(resolver.toSerializationFormat(get.replyTo))
        .build()
        .writeTo(codedOutputStream)
      codedOutputStream.flush()

    case reply: GetNumberOfProcessesReply =>
      val codedOutputStream = CodedOutputStream.newInstance(buf)
      ShardingMessages.GetNumberOfProcessesReply
        .newBuilder()
        .setRevision(reply.revision)
        .setRescaleInProgress(reply.rescaleInProgress)
        .setNumberOfProcesses(reply.numberOfProcesses)
        .setStartedTimestampMillis(reply.started.toEpochMilli)
        .build()
        .writeTo(codedOutputStream)
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

    case DaemonProcessStateManifest =>
      val st = ShardingMessages.DaemonProcessScaleState.parseFrom(buf)
      ShardedDaemonProcessState(
        st.getRevision,
        st.getNumberOfProcesses,
        st.getCompleted,
        Instant.ofEpochMilli(st.getStartedTimestampMillis))

    case ChangeNumberOfProcessesManifest =>
      val change = ShardingMessages.ChangeNumberOfProcesses.parseFrom(buf)
      ChangeNumberOfProcesses(change.getNewNumberOfProcesses, resolver.resolveActorRef(change.getReplyTo))

    case GetNumberOfProcessesManifest =>
      val get = ShardingMessages.GetNumberOfProcesses.parseFrom(buf)
      GetNumberOfProcesses(resolver.resolveActorRef(get.getReplyTo))

    case GetNumberOfProcessesReplyManifest =>
      val reply = ShardingMessages.GetNumberOfProcessesReply.parseFrom(buf)
      GetNumberOfProcessesReply(
        reply.getNumberOfProcesses,
        Instant.ofEpochMilli(reply.getStartedTimestampMillis),
        reply.getRescaleInProgress,
        reply.getRevision)

    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

}

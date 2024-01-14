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

package org.apache.pekko.serialization

import java.nio.{ BufferOverflowException, ByteBuffer }
import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API: only public by configuration
 */
@InternalApi private[pekko] final class LongSerializer(val system: ExtendedActorSystem)
    extends Serializer
    with ByteBufferSerializer {
  override def includeManifest: Boolean = false

  override val identifier: Int = BaseSerializer.identifierFromConfig("primitive-long", system)

  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = {
    buf.putLong(Long.unbox(o))
  }

  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = {
    Long.box(buf.getLong)
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val result = new Array[Byte](8)
    var long = Long.unbox(o)
    var i = 0
    while (long != 0) {
      result(i) = (long & 0xFF).toByte
      i += 1
      long >>>= 8
    }
    result
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    var result = 0L
    var i = 7
    while (i >= 0) {
      result <<= 8
      result |= (bytes(i).toLong & 0xFF)
      i -= 1
    }
    Long.box(result)
  }
}

/**
 * INTERNAL API: only public by configuration
 */
@InternalApi private[pekko] final class IntSerializer(val system: ExtendedActorSystem)
    extends Serializer
    with ByteBufferSerializer {
  override def includeManifest: Boolean = false

  override val identifier: Int = BaseSerializer.identifierFromConfig("primitive-int", system)

  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = buf.putInt(Int.unbox(o))

  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = Int.box(buf.getInt)

  override def toBinary(o: AnyRef): Array[Byte] = {
    val result = new Array[Byte](4)
    var int = Int.unbox(o)
    var i = 0
    while (int != 0) {
      result(i) = (int & 0xFF).toByte
      i += 1
      int >>>= 8
    }
    result
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    var result = 0
    var i = 3
    while (i >= 0) {
      result <<= 8
      result |= (bytes(i).toInt & 0xFF)
      i -= 1
    }
    Int.box(result)
  }
}

/**
 * INTERNAL API: only public by configuration
 */
@InternalApi private[pekko] final class StringSerializer(val system: ExtendedActorSystem)
    extends Serializer
    with ByteBufferSerializer {
  override def includeManifest: Boolean = false

  override val identifier: Int = BaseSerializer.identifierFromConfig("primitive-string", system)

  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = buf.put(toBinary(o))

  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = {
    val bytes = new Array[Byte](buf.remaining())
    buf.get(bytes)
    new String(bytes, StandardCharsets.UTF_8)
  }

  override def toBinary(o: AnyRef): Array[Byte] = o.asInstanceOf[String].getBytes(StandardCharsets.UTF_8)

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    new String(bytes, StandardCharsets.UTF_8)

}

/**
 * INTERNAL API: only public by configuration
 */
@InternalApi private[pekko] final class ByteStringSerializer(val system: ExtendedActorSystem)
    extends Serializer
    with ByteBufferSerializer {
  override def includeManifest: Boolean = false

  override val identifier: Int = BaseSerializer.identifierFromConfig("primitive-bytestring", system)

  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = {
    val bs = o.asInstanceOf[ByteString]

    // ByteString.copyToBuffer does not throw BufferOverflowException
    if (bs.copyToBuffer(buf) < bs.length)
      throw new BufferOverflowException()
  }

  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef =
    ByteString.fromByteBuffer(buf)

  override def toBinary(o: AnyRef): Array[Byte] = {
    val bs = o.asInstanceOf[ByteString]
    val result = new Array[Byte](bs.length)
    bs.copyToArray(result, 0, bs.length)
    result
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    ByteString(bytes)
  }

}

/**
 * INTERNAL API: only public by configuration
 */
@InternalApi private[pekko] final class BooleanSerializer(val system: ExtendedActorSystem)
    extends Serializer
    with ByteBufferSerializer {

  import java.lang.Boolean.{ FALSE, TRUE }

  private val FalseB = 0.toByte
  private val TrueB = 1.toByte

  override def includeManifest: Boolean = false

  override val identifier: Int = BaseSerializer.identifierFromConfig("primitive-boolean", system)

  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = {
    val flag = o match {
      case TRUE  => TrueB
      case FALSE => FalseB
      case b     => throw new IllegalArgumentException(s"Non boolean flag: $b")
    }
    buf.put(flag)
  }

  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = {
    buf.get() match {
      case TrueB  => TRUE
      case FalseB => FALSE
      case b      => throw new IllegalArgumentException(s"Non boolean flag byte: $b")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val flag = o match {
      case TRUE  => TrueB
      case FALSE => FalseB
      case b     => throw new IllegalArgumentException(s"Non boolean flag: $b")
    }
    val result = new Array[Byte](1)
    result(0) = flag
    result
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    bytes(0) match {
      case TrueB  => TRUE
      case FalseB => FALSE
      case b      => throw new IllegalArgumentException(s"Non boolean flag byte: $b")
    }
  }
}

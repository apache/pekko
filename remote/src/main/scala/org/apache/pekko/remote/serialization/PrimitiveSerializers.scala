/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.serialization

import java.nio.ByteBuffer

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.serialization.BaseSerializer
import pekko.serialization.ByteBufferSerializer

@deprecated("Moved to org.apache.pekko.serialization.LongSerializer in pekko-actor", "2.6.0")
class LongSerializer(val system: ExtendedActorSystem) extends BaseSerializer with ByteBufferSerializer {
  // this serializer is not used unless someone is instantiating it manually, it's not in config
  private val delegate = new pekko.serialization.LongSerializer(system)

  override def includeManifest: Boolean = delegate.includeManifest
  override val identifier: Int = delegate.identifier
  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = delegate.toBinary(o, buf)
  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = delegate.fromBinary(buf, manifest)
  override def toBinary(o: AnyRef): Array[Byte] = delegate.toBinary(o)
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = delegate.fromBinary(bytes, manifest)
}

@deprecated("Moved to org.apache.pekko.serialization.IntSerializer in pekko-actor", "2.6.0")
class IntSerializer(val system: ExtendedActorSystem) extends BaseSerializer with ByteBufferSerializer {
  // this serializer is not used unless someone is instantiating it manually, it's not in config
  private val delegate = new pekko.serialization.IntSerializer(system)

  override def includeManifest: Boolean = delegate.includeManifest
  override val identifier: Int = delegate.identifier
  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = delegate.toBinary(o, buf)
  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = delegate.fromBinary(buf, manifest)
  override def toBinary(o: AnyRef): Array[Byte] = delegate.toBinary(o)
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = delegate.fromBinary(bytes, manifest)
}

@deprecated("Moved to org.apache.pekko.serialization.StringSerializer in pekko-actor", "2.6.0")
class StringSerializer(val system: ExtendedActorSystem) extends BaseSerializer with ByteBufferSerializer {
  // this serializer is not used unless someone is instantiating it manually, it's not in config
  private val delegate = new pekko.serialization.StringSerializer(system)

  override def includeManifest: Boolean = delegate.includeManifest
  override val identifier: Int = delegate.identifier
  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = delegate.toBinary(o, buf)
  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = delegate.fromBinary(buf, manifest)
  override def toBinary(o: AnyRef): Array[Byte] = delegate.toBinary(o)
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = delegate.fromBinary(bytes, manifest)
}

@deprecated("Moved to org.apache.pekko.serialization.ByteStringSerializer in pekko-actor", "2.6.0")
class ByteStringSerializer(val system: ExtendedActorSystem) extends BaseSerializer with ByteBufferSerializer {
  // this serializer is not used unless someone is instantiating it manually, it's not in config
  private val delegate = new org.apache.pekko.serialization.ByteStringSerializer(system)

  override def includeManifest: Boolean = delegate.includeManifest
  override val identifier: Int = delegate.identifier
  override def toBinary(o: AnyRef, buf: ByteBuffer): Unit = delegate.toBinary(o, buf)
  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = delegate.fromBinary(buf, manifest)
  override def toBinary(o: AnyRef): Array[Byte] = delegate.toBinary(o)
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = delegate.fromBinary(bytes, manifest)
}

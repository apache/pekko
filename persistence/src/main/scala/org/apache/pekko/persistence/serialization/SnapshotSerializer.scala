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

package org.apache.pekko.persistence.serialization

import java.io._

import org.apache.pekko
import pekko.actor._
import pekko.serialization._
import pekko.util.ByteString.UTF_8

/**
 * Wrapper for snapshot `data`. Snapshot `data` are the actual snapshot objects captured by
 * the persistent actor.
 *
 * @see [[SnapshotSerializer]]
 */
@SerialVersionUID(1L)
final case class Snapshot(data: Any)

/**
 * [[Snapshot]] serializer.
 */
class SnapshotSerializer(val system: ExtendedActorSystem) extends BaseSerializer {

  override val includeManifest: Boolean = false

  private lazy val serialization = SerializationExtension(system)

  /**
   * Serializes a [[Snapshot]]. Delegates serialization of snapshot `data` to a matching
   * `org.apache.pekko.serialization.Serializer`.
   */
  def toBinary(o: AnyRef): Array[Byte] = o match {
    case Snapshot(data) => snapshotToBinary(data.asInstanceOf[AnyRef])
    case _              => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  /**
   * Deserializes a [[Snapshot]]. Delegates deserialization of snapshot `data` to a matching
   * `org.apache.pekko.serialization.Serializer`.
   */
  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    Snapshot(snapshotFromBinary(bytes))

  private def headerToBinary(snapshot: AnyRef, snapshotSerializer: Serializer): Array[Byte] = {
    val out = new ByteArrayOutputStream
    writeInt(out, snapshotSerializer.identifier)

    val ms = Serializers.manifestFor(snapshotSerializer, snapshot)
    if (ms.nonEmpty) out.write(ms.getBytes(UTF_8))

    out.toByteArray
  }

  private def headerFromBinary(bytes: Array[Byte]): (Int, String) = {
    val in = new ByteArrayInputStream(bytes)
    val serializerId = readInt(in)

    if ((serializerId & 0xEDAC) == 0xEDAC) // Java Serialization magic value
      throw new NotSerializableException(s"Replaying snapshot from akka 2.3.x version is not supported any more")

    val remaining = in.available
    val manifest =
      if (remaining == 0) ""
      else {
        val manifestBytes = new Array[Byte](remaining)
        in.read(manifestBytes)
        new String(manifestBytes, UTF_8)
      }
    (serializerId, manifest)
  }

  private def snapshotToBinary(snapshot: AnyRef): Array[Byte] = {
    def serialize() = {
      val snapshotSerializer = serialization.findSerializerFor(snapshot)

      val headerBytes = headerToBinary(snapshot, snapshotSerializer)

      val out = new ByteArrayOutputStream

      writeInt(out, headerBytes.length)

      out.write(headerBytes)
      out.write(snapshotSerializer.toBinary(snapshot))
      out.toByteArray
    }

    val oldInfo = Serialization.currentTransportInformation.value
    try {
      if (oldInfo eq null)
        Serialization.currentTransportInformation.value = system.provider.serializationInformation
      serialize()
    } finally Serialization.currentTransportInformation.value = oldInfo
  }

  private def snapshotFromBinary(bytes: Array[Byte]): AnyRef = {
    val headerLength = readInt(new ByteArrayInputStream(bytes))
    val headerBytes = bytes.slice(4, headerLength + 4)
    val snapshotBytes = bytes.drop(headerLength + 4)

    val (serializerId, manifest) = headerFromBinary(headerBytes)

    // suggested in https://github.com/scullxbones/pekko-persistence-mongo/pull/14#issuecomment-1847223850
    serialization
      .deserialize(snapshotBytes, serializerId, manifest)
      .recover {
        case _: NotSerializableException if manifest.startsWith("akka") =>
          serialization
            .deserialize(snapshotBytes, serializerId, manifest.replaceFirst("akka", "org.apache.pekko"))
      }
      .get
  }

  private def writeInt(out: OutputStream, i: Int): Unit = {
    out.write(i >>> 0)
    out.write(i >>> 8)
    out.write(i >>> 16)
    out.write(i >>> 24)
  }

  private def readInt(in: InputStream): Int = {
    val b1 = in.read
    val b2 = in.read
    val b3 = in.read
    val b4 = in.read

    if ((b1 | b2 | b3 | b3) == -1) throw new EOFException

    (b4 << 24) | (b3 << 16) | (b2 << 8) | b1
  }

}

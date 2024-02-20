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

import scala.collection.immutable
import org.apache.pekko
import pekko.actor.ActorSelectionMessage
import pekko.actor.ExtendedActorSystem
import pekko.actor.SelectChildName
import pekko.actor.SelectChildPattern
import pekko.actor.SelectParent
import pekko.actor.SelectionPathElement
import pekko.protobufv3.internal.ByteString
import pekko.remote.ByteStringUtils
import pekko.remote.ContainerFormats
import pekko.serialization.{ BaseSerializer, SerializationExtension, Serializers }
import pekko.util.ccompat._

@ccompatUsedUntil213
class MessageContainerSerializer(val system: ExtendedActorSystem) extends BaseSerializer {

  private lazy val serialization = SerializationExtension(system)

  def includeManifest: Boolean = false

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case sel: ActorSelectionMessage => serializeSelection(sel)
    case _                          => throw new IllegalArgumentException(s"Cannot serialize object of type [${obj.getClass.getName}]")
  }

  import ContainerFormats.PatternType._

  private def serializeSelection(sel: ActorSelectionMessage): Array[Byte] = {
    val builder = ContainerFormats.SelectionEnvelope.newBuilder()
    val message = sel.msg.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(message)
    builder
      .setEnclosedMessage(ByteStringUtils.toProtoByteStringUnsafe(serializer.toBinary(message)))
      .setSerializerId(serializer.identifier)
      .setWildcardFanOut(sel.wildcardFanOut)

    val ms = Serializers.manifestFor(serializer, message)
    if (ms.nonEmpty) builder.setMessageManifest(ByteString.copyFromUtf8(ms))

    sel.elements.foreach {
      case SelectChildName(name) =>
        builder.addPattern(buildPattern(Some(name), CHILD_NAME))
      case SelectChildPattern(patternStr) =>
        builder.addPattern(buildPattern(Some(patternStr), CHILD_PATTERN))
      case SelectParent =>
        builder.addPattern(buildPattern(None, PARENT))
    }

    builder.build().toByteArray
  }

  private def buildPattern(
      matcher: Option[String],
      tpe: ContainerFormats.PatternType): ContainerFormats.Selection.Builder = {
    val builder = ContainerFormats.Selection.newBuilder().setType(tpe)
    matcher.foreach(builder.setMatcher)
    builder
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = {
    val selectionEnvelope = ContainerFormats.SelectionEnvelope.parseFrom(bytes)
    val manifest = if (selectionEnvelope.hasMessageManifest) selectionEnvelope.getMessageManifest.toStringUtf8 else ""
    val msg = serialization
      .deserialize(selectionEnvelope.getEnclosedMessage.toByteArray, selectionEnvelope.getSerializerId, manifest)
      .get

    import pekko.util.ccompat.JavaConverters._
    val elements: immutable.Iterable[SelectionPathElement] = selectionEnvelope.getPatternList.asScala.iterator
      .map { x =>
        x.getType match {
          case CHILD_NAME    => SelectChildName(x.getMatcher)
          case CHILD_PATTERN => SelectChildPattern(x.getMatcher)
          case PARENT        => SelectParent
        }

      }
      .to(immutable.IndexedSeq)
    val wildcardFanOut = if (selectionEnvelope.hasWildcardFanOut) selectionEnvelope.getWildcardFanOut else false
    ActorSelectionMessage(msg, elements, wildcardFanOut)
  }
}

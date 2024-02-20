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

package org.apache.pekko.actor.typed.internal

import java.io.NotSerializableException
import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.actor.typed.{ ActorRef, ActorRefResolver }
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi
import pekko.serialization.{ BaseSerializer, SerializerWithStringManifest }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class MiscMessageSerializer(val system: pekko.actor.ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  // Serializers are initialized early on. `toTyped` might then try to initialize the classic ActorSystemAdapter extension.
  private lazy val resolver = ActorRefResolver(system.toTyped)
  private val ActorRefManifest = "a"

  def manifest(o: AnyRef): String = o match {
    case _: ActorRef[?] => ActorRefManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  def toBinary(o: AnyRef): Array[Byte] = o match {
    case ref: ActorRef[?] => resolver.toSerializationFormat(ref).getBytes(StandardCharsets.UTF_8)
    case _ =>
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  def fromBinary(bytes: Array[Byte], manifest: String): ActorRef[Any] = manifest match {
    case ActorRefManifest => resolver.resolveActorRef(new String(bytes, StandardCharsets.UTF_8))
    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

}

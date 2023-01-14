/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.receptionist

import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.actor.typed.receptionist.ServiceKey
import pekko.annotation.InternalApi
import pekko.serialization.{ BaseSerializer, SerializerWithStringManifest }

/**
 * Internal API
 */
@InternalApi
final class ServiceKeySerializer(val system: pekko.actor.ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {
  def manifest(o: AnyRef): String = o match {
    case key: DefaultServiceKey[_] => key.typeName
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  def toBinary(o: AnyRef): Array[Byte] = o match {
    case serviceKey: DefaultServiceKey[_] => serviceKey.id.getBytes(StandardCharsets.UTF_8)
    case _ =>
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  def fromBinary(bytes: Array[Byte], manifest: String): ServiceKey[Any] =
    DefaultServiceKey[Any](new String(bytes, StandardCharsets.UTF_8), manifest)
}

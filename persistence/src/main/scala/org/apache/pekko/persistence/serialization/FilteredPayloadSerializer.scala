/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.serialization

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.FilteredPayload
import pekko.serialization.BaseSerializer
import pekko.serialization.SerializerWithStringManifest

/**
 * INTERNAL API
 */
@InternalApi
final class FilteredPayloadSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  private val FilteredPayloadManifest = "F"

  override def manifest(o: AnyRef): String = o match {
    case FilteredPayload => FilteredPayloadManifest
    case _               => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case FilteredPayload => Array.empty
    case _               => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = FilteredPayload

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.internal

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
import pekko.annotation.InternalApi
import pekko.persistence.SnapshotMetadata
import pekko.persistence.testkit.SnapshotStorage
import pekko.serialization.{ Serialization, SerializationExtension, Serializers }

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] class SerializedSnapshotStorageImpl(system: ActorSystem) extends SnapshotStorage {

  override type InternalRepr = (SnapshotMetadata, String, Int, Array[Byte])

  private lazy val serialization = SerializationExtension(system)

  override def toRepr(internal: (SnapshotMetadata, String, Int, Array[Byte])): (SnapshotMetadata, Any) =
    (internal._1, serialization.deserialize(internal._4, internal._3, internal._2).get)

  override def toInternal(repr: (SnapshotMetadata, Any)): (SnapshotMetadata, String, Int, Array[Byte]) =
    Serialization.withTransportInformation(system.asInstanceOf[ExtendedActorSystem]) { () =>
      val payload = repr._2.asInstanceOf[AnyRef]
      val s = serialization.findSerializerFor(payload)
      val manifest = Serializers.manifestFor(s, payload)
      (repr._1, manifest, s.identifier, s.toBinary(payload))
    }

}

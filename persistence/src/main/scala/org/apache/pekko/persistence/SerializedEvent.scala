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

package org.apache.pekko.persistence

/**
 * Some journal implementations may support events of this type by writing the event payload and
 * serialization information without having to serialize it.
 *
 * @param bytes the already serialized event payload
 * @param serializerId the identifier of the serializer that was used to serialize the event
 * @param serializerManifest the manifest of the serializer that was used, empty string if none
 */
final class SerializedEvent(val bytes: Array[Byte], val serializerId: Int, val serializerManifest: String)

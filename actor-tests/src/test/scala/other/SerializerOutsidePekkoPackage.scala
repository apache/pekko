/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package other

import java.nio.charset.StandardCharsets

import org.apache.pekko.serialization.SerializerWithStringManifest

class SerializerOutsidePekkoPackage extends SerializerWithStringManifest {
  override def identifier: Int = 999

  override def manifest(o: AnyRef): String = "A"

  override def toBinary(o: AnyRef): Array[Byte] =
    o.toString.getBytes(StandardCharsets.UTF_8)

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    new String(bytes, StandardCharsets.UTF_8)
}

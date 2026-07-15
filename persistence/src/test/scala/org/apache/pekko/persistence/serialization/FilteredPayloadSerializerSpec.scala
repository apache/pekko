/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.serialization

import org.apache.pekko

import pekko.persistence.FilteredPayload
import pekko.serialization.SerializationExtension
import pekko.serialization.SerializerWithStringManifest
import pekko.testkit.PekkoSpec

class FilteredPayloadSerializerSpec extends PekkoSpec {

  "FilteredPayload serializer" should {
    "serialize FilteredPayload to zero-byte array" in {
      val serialization = SerializationExtension(system)
      val serializer = serialization.findSerializerFor(FilteredPayload).asInstanceOf[SerializerWithStringManifest]
      val manifest = serializer.manifest(FilteredPayload)
      val serialized = serializer.toBinary(FilteredPayload)
      serialized should have(size(0))
      serializer.fromBinary(serialized, manifest) should be(FilteredPayload)
    }
  }

}

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

package org.apache.pekko.persistence.serialization

import org.apache.pekko
import pekko.persistence.PersistentRepr
import pekko.serialization.SerializationExtension
import pekko.testkit.PekkoSpec

class MessageSerializerSpec extends PekkoSpec {

  "Message serializer" should {
    "serialize metadata for persistent repr" in {
      val pr = PersistentRepr("payload", 1L, "pid1").withMetadata("meta")
      val serialization = SerializationExtension(system)
      val deserialzied = serialization.deserialize(serialization.serialize(pr).get, classOf[PersistentRepr]).get
      deserialzied.metadata shouldEqual Some("meta")
    }
  }

}

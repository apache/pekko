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

package doc.org.apache.pekko.serialization.jackson.v1withv2

// #forward-one-rename
import org.apache.pekko.serialization.jackson.JacksonMigration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

class ItemAddedMigration extends JacksonMigration {

  // Data produced in this node is still produced using the version 1 of the schema
  override def currentVersion: Int = 1

  override def supportedForwardVersion: Int = 2

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val root = json.asInstanceOf[ObjectNode]
    if (fromVersion == 2) {
      // When receiving an event of version 2 we down-cast it to the version 1 of the schema
      root.set[JsonNode]("productId", root.get("itemId"))
      root.remove("itemId")
    }
    root
  }
}
// #forward-one-rename

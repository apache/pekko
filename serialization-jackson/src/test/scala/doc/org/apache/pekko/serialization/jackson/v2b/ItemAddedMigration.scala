/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson.v2b

// #add-mandatory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.pekko.serialization.jackson.JacksonMigration

class ItemAddedMigration extends JacksonMigration {

  override def currentVersion: Int = 2

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val root = json.asInstanceOf[ObjectNode]
    if (fromVersion <= 1) {
      root.set("discount", DoubleNode.valueOf(0.0))
    }
    root
  }
}
// #add-mandatory

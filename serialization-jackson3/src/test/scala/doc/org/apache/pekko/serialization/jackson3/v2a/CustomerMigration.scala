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

package doc.org.apache.pekko.serialization.jackson3.v2a

// #structural
import org.apache.pekko.serialization.jackson3.JacksonMigration
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

class CustomerMigration extends JacksonMigration {

  override def currentVersion: Int = 2

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val root = json.asInstanceOf[ObjectNode]
    if (fromVersion <= 1) {
      val shippingAddress = root.withObject("shippingAddress")
      shippingAddress.set[JsonNode]("street", root.get("street"))
      shippingAddress.set[JsonNode]("city", root.get("city"))
      shippingAddress.set[JsonNode]("zipCode", root.get("zipCode"))
      shippingAddress.set[JsonNode]("country", root.get("country"))
      root.remove("street")
      root.remove("city")
      root.remove("zipCode")
      root.remove("country")
    }
    root
  }
}
// #structural

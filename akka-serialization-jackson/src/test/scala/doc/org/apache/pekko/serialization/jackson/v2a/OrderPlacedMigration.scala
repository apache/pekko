/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson.v2a

import org.apache.pekko.serialization.jackson.JacksonMigration
import com.fasterxml.jackson.databind.JsonNode

// #rename-class
class OrderPlacedMigration extends JacksonMigration {

  override def currentVersion: Int = 2

  override def transformClassName(fromVersion: Int, className: String): String =
    classOf[OrderPlaced].getName

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = json
}
// #rename-class

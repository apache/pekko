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

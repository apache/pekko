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

package org.apache.pekko.serialization.jackson3

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.ObjectNode

object ScalaTestEventMigration {
  def upcastV1ToV2(root: ObjectNode): ObjectNode = {
    root.set("field1V2", root.get("field1"))
    root.remove("field1")
    root.set("field2", IntNode.valueOf(17))
    root
  }

  def upcastV2ToV3(root: ObjectNode): ObjectNode = {
    root.set("field3", root.get("field2"))
    root.remove("field2")
    root
  }

  def downcastV3ToV2(root: ObjectNode) = {
    // downcast the V3 representation to the V2 representation. A field
    // is renamed.
    root.set("field2", root.get("field3"))
    root.remove("field3")
    root
  }

}

class ScalaTestEventMigrationV2 extends JacksonMigration {
  import ScalaTestEventMigration._

  override def currentVersion = 2

  override def transformClassName(fromVersion: Int, className: String): String =
    classOf[ScalaTestMessages.Event2].getName

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val root = json.asInstanceOf[ObjectNode]
    upcastV1ToV2(root)
  }

}

class ScalaTestEventMigrationV2WithV3 extends JacksonMigration {
  import ScalaTestEventMigration._

  override def currentVersion = 2

  override def supportedForwardVersion: Int = 3

  // Always produce the type of the currentVersion. When fromVersion is lower,
  // transform will lift it. When fromVersion is higher, transform will downcast it.
  override def transformClassName(fromVersion: Int, className: String): String =
    classOf[ScalaTestMessages.Event2].getName

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    var root = json.asInstanceOf[ObjectNode]
    if (fromVersion < 2) {
      root = upcastV1ToV2(root)
    }
    if (fromVersion == 3) {
      root = downcastV3ToV2(root)
    }
    root
  }

}

class ScalaTestEventMigrationV3 extends JacksonMigration {
  import ScalaTestEventMigration._

  override def currentVersion = 3

  override def transformClassName(fromVersion: Int, className: String): String =
    classOf[ScalaTestMessages.Event3].getName

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    var root = json.asInstanceOf[ObjectNode]
    if (fromVersion < 2) {
      root = upcastV1ToV2(root)
    }
    if (fromVersion < 3) {
      root = upcastV2ToV3(root)
    }
    root
  }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JavaTestEventMigrationV2 extends JacksonMigration {

  @Override
  public int currentVersion() {
    return 2;
  }

  @Override
  public String transformClassName(int fromVersion, String className) {
    // Ignore the incoming manifest and produce the same class name always.
    return JavaTestMessages.Event2.class.getName();
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    ObjectNode root = (ObjectNode) json;
    root.set("field1V2", root.get("field1"));
    root.remove("field1");
    root.set("field2", IntNode.valueOf(17));
    return root;
  }
}

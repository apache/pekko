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

package jdoc.org.apache.pekko.serialization.jackson3.v2b;

// #add-mandatory
import org.apache.pekko.serialization.jackson3.JacksonMigration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

public class ItemAddedMigration extends JacksonMigration {

  @Override
  public int currentVersion() {
    return 2;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    ObjectNode root = (ObjectNode) json;
    if (fromVersion <= 1) {
      root.set("discount", DoubleNode.valueOf(0.0));
    }
    return root;
  }
}
// #add-mandatory

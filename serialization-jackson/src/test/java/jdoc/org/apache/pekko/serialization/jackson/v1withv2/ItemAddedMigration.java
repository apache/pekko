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

package jdoc.org.apache.pekko.serialization.jackson.v1withv2;

// #forward-one-rename

import org.apache.pekko.serialization.jackson.JacksonMigration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ItemAddedMigration extends JacksonMigration {

  // Data produced in this node is still produced using the version 1 of the schema
  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public int supportedForwardVersion() {
    return 2;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    ObjectNode root = (ObjectNode) json;
    if (fromVersion == 2) {
      // When receiving an event of version 2 we down-cast it to the version 1 of the schema
      root.set("productId", root.get("itemId"));
      root.remove("itemId");
    }
    return root;
  }
}
// #forward-one-rename

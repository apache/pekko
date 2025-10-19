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

package jdoc.org.apache.pekko.serialization.jackson.v2a;

// #structural
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.serialization.jackson.JacksonMigration;

public class CustomerMigration extends JacksonMigration {

  @Override
  public int currentVersion() {
    return 2;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    ObjectNode root = (ObjectNode) json;
    if (fromVersion <= 1) {
      ObjectNode shippingAddress = root.withObject("shippingAddress");
      shippingAddress.set("street", root.get("street"));
      shippingAddress.set("city", root.get("city"));
      shippingAddress.set("zipCode", root.get("zipCode"));
      shippingAddress.set("country", root.get("country"));
      root.remove("street");
      root.remove("city");
      root.remove("zipCode");
      root.remove("country");
    }
    return root;
  }
}
// #structural

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata;

public class ORMultiMapTest {

  public void compileOnlyORMultiMapTest() {
    // primarily to check API accessibility with overloads/types
    SelfUniqueAddress node = null;
    ORMultiMap<String, String> orMultiMap = ORMultiMap.create();
    orMultiMap.addBinding(node, "a", "1");
    orMultiMap.removeBinding(node, "a", "1");
  }
}

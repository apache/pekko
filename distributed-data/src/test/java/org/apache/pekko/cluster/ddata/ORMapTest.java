/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata;

public class ORMapTest {

  public void compileOnlyORMapTest() {
    // primarily to check API accessibility with overloads/types
    SelfUniqueAddress node1 = null;

    ORMap<String, PNCounterMap<String>> orMap = ORMap.create();
    // updated needs a cast
    ORMap<String, PNCounterMap<String>> updated =
        orMap.update(node1, "key", PNCounterMap.create(), curr -> curr.decrement(node1, "key", 10));
    updated.getEntries();
  }
}

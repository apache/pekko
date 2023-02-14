/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Address;

import java.util.Collections;
import java.util.List;

// Doc code, compile only
@SuppressWarnings("ConstantConditions")
public class ClusterJavaCompileTest {

  final ActorSystem system = null;
  final Cluster cluster = null;

  public void compileJoinSeedNodesInJava() {
    final List<Address> addresses = Collections.singletonList(new Address("pekko", "MySystem"));
    cluster.joinSeedNodes(addresses);
  }
}

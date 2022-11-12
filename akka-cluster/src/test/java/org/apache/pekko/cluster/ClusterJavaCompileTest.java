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
    final List<Address> addresses = Collections.singletonList(new Address("akka", "MySystem"));
    cluster.joinSeedNodes(addresses);
  }
}

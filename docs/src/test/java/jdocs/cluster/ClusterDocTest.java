/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import org.apache.pekko.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import jdocs.AbstractJavaTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

// #join-seed-nodes-imports
import org.apache.pekko.actor.Address;
import org.apache.pekko.cluster.Cluster;

// #join-seed-nodes-imports
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.Member;

public class ClusterDocTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system =
        ActorSystem.create(
            "ClusterDocTest",
            ConfigFactory.parseString(scala.docs.cluster.ClusterDocSpec.config()));
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void demonstrateLeave() {
    // #leave
    final Cluster cluster = Cluster.get(system);
    cluster.leave(cluster.selfAddress());
    // #leave

  }

  // compile only
  @SuppressWarnings("unused")
  public void demonstrateDataCenter() {
    // #dcAccess
    final Cluster cluster = Cluster.get(system);
    // this node's data center
    String dc = cluster.selfDataCenter();
    // all known data centers
    Set<String> allDc = cluster.state().getAllDataCenters();
    // a specific member's data center
    Member aMember = cluster.state().getMembers().iterator().next();
    String aDc = aMember.dataCenter();
    // #dcAccess
  }

  // compile only
  @SuppressWarnings("unused")
  public void demonstrateJoinSeedNodes() {
    // #join-seed-nodes
    final Cluster cluster = Cluster.get(system);
    List<Address> list =
        new LinkedList<>(); // replace this with your method to dynamically get seed nodes
    cluster.joinSeedNodes(list);
    // #join-seed-nodes
  }
}

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

package jdocs.actor;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import jdocs.AbstractJavaTest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class SampleActorTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("SampleActorTest");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testSampleActor() {
    new TestKit(system) {
      {
        final ActorRef subject = system.actorOf(Props.create(SampleActor.class), "sample-actor");
        final ActorRef probeRef = getRef();

        subject.tell(47.11, probeRef);
        subject.tell("and no guard in the beginning", probeRef);
        subject.tell("guard is a good thing", probeRef);
        subject.tell(47.11, probeRef);
        subject.tell(4711, probeRef);
        subject.tell("and no guard in the beginning", probeRef);
        subject.tell(4711, probeRef);
        subject.tell("and an unmatched message", probeRef);

        expectMsgEquals(47.11);
        assertTrue(expectMsgClass(String.class).startsWith("startsWith(guard):"));
        assertTrue(expectMsgClass(String.class).startsWith("contains(guard):"));
        expectMsgEquals(47110);
        expectNoMessage();
      }
    };
  }
}

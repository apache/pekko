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

package jdocs.stream.javadsl.cookbook;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeFlattenList extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeFlattenList");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void workWithMapConcat() throws Exception {
    new TestKit(system) {
      {
        Source<List<Message>, NotUsed> someDataSource =
            Source.from(
                Arrays.asList(
                    Arrays.asList(new Message("1")),
                    Arrays.asList(new Message("2"), new Message("3"))));

        // #flattening-lists
        Source<List<Message>, NotUsed> myData = someDataSource;
        Source<Message, NotUsed> flattened = myData.mapConcat(i -> i);
        // #flattening-lists

        List<Message> got =
            flattened
                .limit(10)
                .runWith(Sink.seq(), system)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        assertEquals(got.get(0), new Message("1"));
        assertEquals(got.get(1), new Message("2"));
        assertEquals(got.get(2), new Message("3"));
      }
    };
  }
}

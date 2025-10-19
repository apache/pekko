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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.*;
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeDroppyBroadcast extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeDroppyBroadcast");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void work() throws Exception {
    new TestKit(system) {
      // #droppy-bcast
      // Makes a sink drop elements if too slow
      public <T> Sink<T, CompletionStage<Done>> droppySink(
          Sink<T, CompletionStage<Done>> sink, int size) {
        return Flow.<T>create().buffer(size, OverflowStrategy.dropHead()).toMat(sink, Keep.right());
      }
      // #droppy-bcast

      {
        final List<Integer> nums = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
          nums.add(i + 1);
        }

        final Sink<Integer, CompletionStage<Done>> mySink1 = Sink.ignore();
        final Sink<Integer, CompletionStage<Done>> mySink2 = Sink.ignore();
        final Sink<Integer, CompletionStage<Done>> mySink3 = Sink.ignore();

        final Source<Integer, NotUsed> myData = Source.from(nums);

        // #droppy-bcast2
        RunnableGraph.fromGraph(
            GraphDSL.create(
                builder -> {
                  final int outputCount = 3;
                  final UniformFanOutShape<Integer, Integer> bcast =
                      builder.add(Broadcast.create(outputCount));
                  builder.from(builder.add(myData)).toFanOut(bcast);
                  builder.from(bcast).to(builder.add(droppySink(mySink1, 10)));
                  builder.from(bcast).to(builder.add(droppySink(mySink2, 10)));
                  builder.from(bcast).to(builder.add(droppySink(mySink3, 10)));
                  return ClosedShape.getInstance();
                }));
        // #droppy-bcast2
      }
    };
  }
}

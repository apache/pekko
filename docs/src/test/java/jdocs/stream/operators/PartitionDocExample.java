/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators;

import org.apache.pekko.actor.ActorSystem;

// #import
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.UniformFanOutShape;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.apache.pekko.stream.javadsl.Partition;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
// #import

public class PartitionDocExample {

  private final ActorSystem system = ActorSystem.create("PartitionDocExample");

  void partitionExample() {

    // #partition

    Source<Integer, NotUsed> source = Source.range(1, 10);

    Sink<Integer, NotUsed> even =
        Flow.of(Integer.class)
            .log("even")
            .withAttributes(Attributes.createLogLevels(Attributes.logLevelInfo()))
            .to(Sink.ignore());
    Sink<Integer, NotUsed> odd =
        Flow.of(Integer.class)
            .log("odd")
            .withAttributes(Attributes.createLogLevels(Attributes.logLevelInfo()))
            .to(Sink.ignore());

    RunnableGraph.fromGraph(
            GraphDSL.create(
                builder -> {
                  UniformFanOutShape<Integer, Integer> partition =
                      builder.add(
                          Partition.create(
                              Integer.class, 2, element -> (element % 2 == 0) ? 0 : 1));
                  builder.from(builder.add(source)).viaFanOut(partition);
                  builder.from(partition.out(0)).to(builder.add(even));
                  builder.from(partition.out(1)).to(builder.add(odd));
                  return ClosedShape.getInstance();
                }))
        .run(system);

    // #partition
  }
}

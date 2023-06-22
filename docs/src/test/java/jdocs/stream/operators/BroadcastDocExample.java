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

package jdocs.stream.operators;

import org.apache.pekko.actor.ActorSystem;

// #import
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.tuple.Tuple3;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.UniformFanOutShape;
import org.apache.pekko.stream.javadsl.Broadcast;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import java.util.concurrent.CompletionStage;
// #import

public class BroadcastDocExample {

  private final ActorSystem system = ActorSystem.create("BroadcastDocExample");

  void broadcastExample() {

    // #broadcast

    Source<Integer, NotUsed> source = Source.range(1, 10);

    Sink<Integer, CompletionStage<Integer>> countSink =
        Flow.of(Integer.class).toMat(Sink.fold(0, (acc, elem) -> acc + 1), Keep.right());
    Sink<Integer, CompletionStage<Integer>> minSink =
        Flow.of(Integer.class).toMat(Sink.fold(0, Math::min), Keep.right());
    Sink<Integer, CompletionStage<Integer>> maxSink =
        Flow.of(Integer.class).toMat(Sink.fold(0, Math::max), Keep.right());

    final Tuple3<CompletionStage<Integer>, CompletionStage<Integer>, CompletionStage<Integer>>
        result =
            RunnableGraph.fromGraph(
                    GraphDSL.create3(
                        countSink,
                        minSink,
                        maxSink,
                        Tuple3::create,
                        (builder, countS, minS, maxS) -> {
                          final UniformFanOutShape<Integer, Integer> broadcast =
                              builder.add(Broadcast.create(3));
                          builder.from(builder.add(source)).viaFanOut(broadcast);
                          builder.from(broadcast.out(0)).to(countS);
                          builder.from(broadcast.out(1)).to(minS);
                          builder.from(broadcast.out(2)).to(maxS);
                          return ClosedShape.getInstance();
                        }))
                .run(system);

    // #broadcast

    // #broadcast-async
    RunnableGraph.fromGraph(
        GraphDSL.create3(
            countSink.async(),
            minSink.async(),
            maxSink.async(),
            Tuple3::create,
            (builder, countS, minS, maxS) -> {
              final UniformFanOutShape<Integer, Integer> broadcast =
                  builder.add(Broadcast.create(3));
              builder.from(builder.add(source)).viaFanOut(broadcast);

              builder.from(broadcast.out(0)).to(countS);
              builder.from(broadcast.out(1)).to(minS);
              builder.from(broadcast.out(2)).to(maxS);
              return ClosedShape.getInstance();
            }));
    // #broadcast-async

  }
}

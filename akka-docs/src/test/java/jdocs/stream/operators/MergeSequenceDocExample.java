/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators;

import org.apache.pekko.actor.ActorSystem;

// #import
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.UniformFanInShape;
import org.apache.pekko.stream.UniformFanOutShape;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.apache.pekko.stream.javadsl.MergeSequence;
import org.apache.pekko.stream.javadsl.Partition;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
// #import

public class MergeSequenceDocExample {

  private final ActorSystem system = ActorSystem.create("MergeSequenceDocExample");

  interface Message {}

  boolean shouldProcess(Message message) {
    return true;
  }

  Source<Message, NotUsed> createSubscription() {
    return null;
  }

  Flow<Pair<Message, Long>, Pair<Message, Long>, NotUsed> createMessageProcessor() {
    return null;
  }

  Sink<Message, NotUsed> createMessageAcknowledger() {
    return null;
  }

  void mergeSequenceExample() {

    // #merge-sequence

    Source<Message, NotUsed> subscription = createSubscription();
    Flow<Pair<Message, Long>, Pair<Message, Long>, NotUsed> messageProcessor =
        createMessageProcessor();
    Sink<Message, NotUsed> messageAcknowledger = createMessageAcknowledger();

    RunnableGraph.fromGraph(
            GraphDSL.create(
                builder -> {
                  // Partitions stream into messages that should or should not be processed
                  UniformFanOutShape<Pair<Message, Long>, Pair<Message, Long>> partition =
                      builder.add(
                          Partition.create(2, element -> shouldProcess(element.first()) ? 0 : 1));
                  // Merges stream by the index produced by zipWithIndex
                  UniformFanInShape<Pair<Message, Long>, Pair<Message, Long>> merge =
                      builder.add(MergeSequence.create(2, Pair::second));

                  builder.from(builder.add(subscription.zipWithIndex())).viaFanOut(partition);
                  // First goes through message processor
                  builder.from(partition.out(0)).via(builder.add(messageProcessor)).viaFanIn(merge);
                  // Second partition bypasses message processor
                  builder.from(partition.out(1)).viaFanIn(merge);

                  // Unwrap message index pairs and send to acknowledger
                  builder
                      .from(merge.out())
                      .to(
                          builder.add(
                              Flow.<Pair<Message, Long>>create()
                                  .map(Pair::first)
                                  .to(messageAcknowledger)));

                  return ClosedShape.getInstance();
                }))
        .run(system);

    // #merge-sequence
  }
}

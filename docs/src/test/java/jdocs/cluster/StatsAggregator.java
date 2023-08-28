/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jdocs.cluster.StatsMessages.JobFailed;
import jdocs.cluster.StatsMessages.StatsResult;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ReceiveTimeout;

// #aggregator
public class StatsAggregator extends AbstractActor {

  final int expectedResults;
  final ActorRef replyTo;
  final List<Integer> results = new ArrayList<Integer>();

  public StatsAggregator(int expectedResults, ActorRef replyTo) {
    this.expectedResults = expectedResults;
    this.replyTo = replyTo;
  }

  @Override
  public void preStart() {
    getContext().setReceiveTimeout(Duration.ofSeconds(3));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            wordCount -> {
              results.add(wordCount);
              if (results.size() == expectedResults) {
                int sum = 0;
                for (int c : results) {
                  sum += c;
                }
                double meanWordLength = ((double) sum) / results.size();
                replyTo.tell(new StatsResult(meanWordLength), getSelf());
                getContext().stop(getSelf());
              }
            })
        .match(
            ReceiveTimeout.class,
            x -> {
              replyTo.tell(new JobFailed("Service unavailable, try again later"), getSelf());
              getContext().stop(getSelf());
            })
        .build();
  }
}
// #aggregator

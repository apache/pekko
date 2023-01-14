/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import java.util.HashMap;
import java.util.Map;

import org.apache.pekko.actor.AbstractActor;

// #worker
public class StatsWorker extends AbstractActor {

  Map<String, Integer> cache = new HashMap<String, Integer>();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            word -> {
              Integer length = cache.get(word);
              if (length == null) {
                length = word.length();
                cache.put(word, length);
              }
              getSender().tell(length, getSelf());
            })
        .build();
  }
}
// #worker

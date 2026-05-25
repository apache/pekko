/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package jdocs.stream.operators;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;

public class JavaCollectorDocExamples {

  static void example() {
    ActorSystem system = null;

    // #javaCollector
    Source<String, NotUsed> source =
        Source.from(Arrays.asList("Apache", "Pekko", "Streams"));

    CompletionStage<List<String>> result =
        source.runWith(StreamConverters.javaCollector(Collectors::toList), system);
    // #javaCollector
  }
}

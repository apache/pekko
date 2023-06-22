/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.sink;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class Lazy {

  private ActorSystem system = null;

  void example() {
    // #simple-example
    CompletionStage<Optional<String>> matVal =
        Source.<String>maybe()
            .map(
                element -> {
                  System.out.println("mapped " + element);
                  return element;
                })
            .toMat(
                Sink.lazySink(
                    () -> {
                      System.out.println("Sink created");
                      return Sink.foreach(elem -> System.out.println("foreach " + elem));
                    }),
                Keep.left())
            .run(system);

    // some time passes
    // nothing has been printed
    matVal.toCompletableFuture().complete(Optional.of("one"));
    // now prints:
    // mapped one
    // Sink created
    // foreach one

    // #simple-example
  }
}

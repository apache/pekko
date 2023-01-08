/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.sourceorflow;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Arrays;

public class MapError {

  public static void main(String[] args) {

    // #map-error

    final ActorSystem system = ActorSystem.create("mapError-operator-example");
    Source.from(Arrays.asList(-1, 0, 1))
        .map(x -> 1 / x)
        .mapError(
            ArithmeticException.class,
            (ArithmeticException e) ->
                new UnsupportedOperationException("Divide by Zero Operation is not supported."))
        .runWith(Sink.seq(), system)
        .whenComplete(
            (result, exception) -> {
              if (result != null) System.out.println(result.toString());
              else System.out.println(exception.getMessage());
            });

    // prints "Divide by Zero Operation is not supported."
    // #map-error
  }
}

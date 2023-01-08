/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Source;

import java.math.BigInteger;
import java.util.Optional;

interface Unfold {

  // #countdown
  public static Source<Integer, NotUsed> countDown(Integer from) {
    return Source.unfold(
        from,
        current -> {
          if (current == 0) return Optional.empty();
          else return Optional.of(Pair.create(current - 1, current));
        });
  }
  // #countdown

  // #fibonacci
  public static Source<BigInteger, NotUsed> fibonacci() {
    return Source.unfold(
        Pair.create(BigInteger.ZERO, BigInteger.ONE),
        current -> {
          BigInteger a = current.first();
          BigInteger b = current.second();
          Pair<BigInteger, BigInteger> next = Pair.create(b, a.add(b));
          return Optional.of(Pair.create(next, a));
        });
  }
  // #fibonacci

}

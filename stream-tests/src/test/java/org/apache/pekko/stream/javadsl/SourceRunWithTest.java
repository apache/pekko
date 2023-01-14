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

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;

import java.util.concurrent.CompletableFuture;

public class SourceRunWithTest {

  public void sourceRunWithCompileOnlyTest() {
    Materializer mat = null;
    ActorSystem system = null;

    // Coverage for #27944
    Source.<String>empty().runWith(Sink.seq(), mat);
    Source.<String>empty().runWith(Sink.seq(), system);

    Source.<Long>empty().runFold(0L, (n, acc) -> n + acc, mat);
    Source.<Long>empty().runFold(0L, (n, acc) -> n + acc, system);

    Source.<Long>empty()
        .runFoldAsync(0L, (n, acc) -> CompletableFuture.completedFuture(n + acc), mat);
    Source.<Long>empty()
        .runFoldAsync(0L, (n, acc) -> CompletableFuture.completedFuture(n + acc), system);

    Source.<String>empty().runReduce((a, b) -> a + b, mat);
    Source.<String>empty().runReduce((a, b) -> a + b, system);

    Source.<String>empty().runForeach(str -> System.out.println(str), mat);
    Source.<String>empty().runForeach(str -> System.out.println(str), system);
  }
}

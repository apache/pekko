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

package jdocs.stream.operators.sourceorflow;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class Limit {
  public void simple() {
    ActorSystem<?> system = null;
    // #simple
    Source<String, NotUsed> untrustedSource = Source.repeat("element");

    CompletionStage<List<String>> elements =
        untrustedSource.limit(10000).runWith(Sink.seq(), system);
    // #simple
  }
}

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

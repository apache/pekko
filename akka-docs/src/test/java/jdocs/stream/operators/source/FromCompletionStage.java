/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

// #sourceFromCompletionStage
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.NotUsed;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.*;

// #sourceFromCompletionStage

class FromCompletionStage {

  public static void sourceFromCompletionStage() {
    // Use one ActorSystem per application
    ActorSystem system = null;

    // #sourceFromCompletionStage
    CompletionStage<Integer> stage = CompletableFuture.completedFuture(10);

    Source<Integer, NotUsed> source = Source.completionStage(stage);

    Sink<Integer, CompletionStage<Done>> sink = Sink.foreach(i -> System.out.println(i.toString()));

    source.runWith(sink, system); // 10
    // #sourceFromCompletionStage
  }
}

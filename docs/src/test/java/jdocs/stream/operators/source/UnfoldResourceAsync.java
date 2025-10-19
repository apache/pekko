/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

public class UnfoldResourceAsync {
  // imaginary async API we need to use
  // #unfoldResource-async-api
  interface Database {
    // async query
    CompletionStage<QueryResult> doQuery();
  }

  interface QueryResult {

    // are there more results
    CompletionStage<Boolean> hasMore();

    // async retrieval of each element
    CompletionStage<DatabaseEntry> nextEntry();

    CompletionStage<Void> close();
  }

  interface DatabaseEntry {}

  // #unfoldResource-async-api

  void unfoldResourceExample() {
    ActorSystem system = null;

    // #unfoldResourceAsync
    // we don't actually have one, it was just made up for the sample
    Database database = null;

    Source<DatabaseEntry, NotUsed> queryResultSource =
        Source.unfoldResourceAsync(
            // open
            database::doQuery,
            // read
            this::readQueryResult,
            // close
            queryResult -> queryResult.close().thenApply(__ -> Done.done()));

    queryResultSource.runForeach(entry -> System.out.println(entry.toString()), system);
    // #unfoldResourceAsync
  }

  // #unfoldResourceAsync
  private CompletionStage<Optional<DatabaseEntry>> readQueryResult(QueryResult queryResult) {
    return queryResult
        .hasMore()
        .thenCompose(
            more -> {
              if (more) {
                return queryResult.nextEntry().thenApply(Optional::of);
              } else {
                return CompletableFuture.completedFuture(Optional.empty());
              }
            });
  }
  // #unfoldResourceAsync
}

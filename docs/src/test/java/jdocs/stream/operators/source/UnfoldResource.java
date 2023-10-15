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
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

interface UnfoldResource {
  // imaginary blocking API we need to use
  // #unfoldResource-blocking-api
  interface Database {
    // blocking query
    QueryResult doQuery();
  }

  interface QueryResult {
    boolean hasMore();
    // potentially blocking retrieval of each element
    DatabaseEntry nextEntry();

    void close();
  }

  interface DatabaseEntry {}

  // #unfoldResource-blocking-api

  default void unfoldResourceExample() {
    ActorSystem system = null;

    // #unfoldResource
    // we don't actually have one, it was just made up for the sample
    Database database = null;

    Source<DatabaseEntry, NotUsed> queryResultSource =
        Source.unfoldResource(
            // open
            () -> database.doQuery(),
            // read
            (queryResult) -> {
              if (queryResult.hasMore()) return Optional.of(queryResult.nextEntry());
              else return Optional.empty();
            },
            // close
            QueryResult::close);

    queryResultSource.runForeach(entry -> System.out.println(entry.toString()), system);
    // #unfoldResource
  }
}

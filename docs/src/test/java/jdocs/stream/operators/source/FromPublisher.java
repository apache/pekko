/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

// #imports
import java.util.concurrent.Flow.Publisher;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

// #imports

public interface FromPublisher {
  static class Row {
    public String getField(String fieldName) {
      throw new UnsupportedOperationException("Not implemented in sample");
    }
  }

  static class DatabaseClient {
    Publisher<Row> fetchRows() {
      throw new UnsupportedOperationException("Not implemented in sample");
    }
  }

  DatabaseClient databaseClient = null;

  // #example
  class Example {
    public Source<String, NotUsed> names() {
      // A new subscriber will subscribe to the supplied publisher for each
      // materialization, so depending on whether the database client supports
      // this the Source can be materialized more than once.
      return Source.<Row>fromPublisher(databaseClient.fetchRows()).map(row -> row.getField("name"));
    }
  }
  // #example
}

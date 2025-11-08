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
import java.util.concurrent.Flow.Subscriber;
import org.apache.pekko.NotUsed;
// #imports
import org.apache.pekko.stream.javadsl.Source;

public interface AsSubscriber {
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
    Source<Row, NotUsed> rowSource =
        Source.<Row>asJavaSubscriber()
            .mapMaterializedValue(
                subscriber -> {
                  // For each materialization, fetch the rows from the database:
                  Publisher<Row> rows = databaseClient.fetchRows();
                  rows.subscribe(subscriber);

                  return NotUsed.getInstance();
                });

    public Source<String, NotUsed> names() {
      // rowSource can be re-used, since it will start a new
      // query for each materialization, fully supporting backpressure
      // for each materialized stream:
      return rowSource.map(row -> row.getField("name"));
    }
  }
  // #example
}

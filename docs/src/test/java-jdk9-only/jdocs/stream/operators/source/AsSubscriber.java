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

package jdocs.stream.operators.source;

//#imports
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Publisher;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.JavaFlowSupport;

//#imports

import org.apache.commons.lang.NotImplementedException;

public interface AsSubscriber {
    // We are 'faking' the JavaFlowSupport API here so we can include the signature as a snippet in the API,
    // because we're not publishing those (jdk9+) classes in our API docs yet.
    static class JavaFlowSupport {
        public static final class Source {
            public
            // #api
            static <T> pekko.stream.javadsl.Source<T, Subscriber<T>> asSubscriber()
            // #api
            {
                return pekko.stream.javadsl.JavaFlowSupport.Source.<T>asSubscriber();
            }
        }
    }

    static class Row {
        public String getField(String fieldName) {
            throw new NotImplementedException();
        }
    }

    static class DatabaseClient {
        Publisher<Row> fetchRows() {
            throw new NotImplementedException();
        }
    }

    DatabaseClient databaseClient = null;

    // #example
    class Example {
        Source<Row, NotUsed> rowSource =
                JavaFlowSupport.Source.<Row>asSubscriber()
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

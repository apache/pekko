/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jdocs.stream.operators.sourceorflow;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public interface MapWithResource {
  // #mapWithResource-blocking-api
  interface DBDriver {
    Connection create(URL url, String userName, String password);
  }

  interface Connection {
    void close();
  }

  interface Database {
    // blocking query
    QueryResult doQuery(Connection connection, String query);
  }

  interface QueryResult {
    boolean hasMore();

    // potentially blocking retrieval of each element
    DatabaseRecord next();

    // potentially blocking retrieval all element
    List<DatabaseRecord> toList();
  }

  interface DatabaseRecord {}
  // #mapWithResource-blocking-api

  default void mapWithResourceExample() {
    final ActorSystem system = null;
    final URL url = null;
    final String userName = "Akka";
    final String password = "Hakking";
    final DBDriver dbDriver = null;
    // #mapWithResource
    // some database for JVM
    final Database db = null;
    Source.from(
            Arrays.asList(
                "SELECT * FROM shop ORDER BY article-0000 order by gmtModified desc limit 100;",
                "SELECT * FROM shop ORDER BY article-0001 order by gmtModified desc limit 100;"))
        .mapWithResource(
            () -> dbDriver.create(url, userName, password),
            (connection, query) -> db.doQuery(connection, query).toList(),
            connection -> {
              connection.close();
              return Optional.empty();
            })
        .mapConcat(elems -> elems)
        .runForeach(System.out::println, system);
    // #mapWithResource
  }
}

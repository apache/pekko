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

package docs.stream.operators.source

//#imports
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Publisher

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.JavaFlowSupport

//#imports

object AsSubscriber {
  case class Row(name: String)

  class DatabaseClient {
    def fetchRows(): Publisher[Row] = ???
  }

  val databaseClient: DatabaseClient = ???

  // #example
  val rowSource: Source[Row, NotUsed] =
    JavaFlowSupport.Source.asSubscriber
      .mapMaterializedValue { (subscriber: Subscriber[Row]) =>
        // For each materialization, fetch the rows from the database:
        val rows: Publisher[Row] = databaseClient.fetchRows()
        rows.subscribe(subscriber)
        NotUsed
      };

  val names: Source[String, NotUsed] =
    // rowSource can be re-used, since it will start a new
    // query for each materialization, fully supporting backpressure
    // for each materialized stream:
    rowSource.map(row => row.name)
  // #example
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
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

object FromPublisher {
  case class Row(name: String)

  class DatabaseClient {
    def fetchRows(): Publisher[Row] = ???
  }

  val databaseClient: DatabaseClient = ???

  // #example
  val names: Source[String, NotUsed] =
    // A new subscriber will subscribe to the supplied publisher for each
    // materialization, so depending on whether the database client supports
    // this the Source can be materialized more than once.
    JavaFlowSupport.Source.fromPublisher(databaseClient.fetchRows())
      .map(row => row.name)
  // #example
}

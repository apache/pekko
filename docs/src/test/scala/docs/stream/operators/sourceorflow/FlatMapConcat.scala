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

package docs.stream.operators.sourceorflow
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object FlatMapConcat {

  implicit val system: ActorSystem = ActorSystem()

  // #flatmap-concat
  val source: Source[String, NotUsed] = Source(List("customer-1", "customer-2"))

  // e.g. could b a query to a database
  def lookupCustomerEvents(customerId: String): Source[String, NotUsed] = {
    Source(List(s"$customerId-event-1", s"$customerId-event-2"))
  }

  source.flatMapConcat(customerId => lookupCustomerEvents(customerId)).runForeach(println)

  // prints - events from each customer consecutively
  // customer-1-event-1
  // customer-1-event-2
  // customer-2-event-1
  // customer-2-event-2
  // #flatmap-concat

}

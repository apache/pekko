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

package docs.stream.operators.sourceorflow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

object FlatMapMerge {

  implicit val system: ActorSystem = ActorSystem()

  // #flatmap-merge
  val source: Source[String, NotUsed] = Source(List("customer-1", "customer-2"))

  // e.g. could b a query to a database
  def lookupCustomerEvents(customerId: String): Source[String, NotUsed] = {
    Source(List(s"$customerId-evt-1", s"$customerId-evt2"))
  }

  source.flatMapMerge(10, customerId => lookupCustomerEvents(customerId)).runForeach(println)

  // prints - events from different customers could interleave
  // customer-1-evt-1
  // customer-2-evt-1
  // customer-1-evt-2
  // customer-2-evt-2
  // #flatmap-merge

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko.stream.testkit.StreamSpec

class SubscriberSourceSpec extends StreamSpec {

  "A SubscriberSource" must {

    "be able to use Subscriber in materialized value transformation" in {
      val f =
        Source
          .asSubscriber[Int]
          .mapMaterializedValue(s => Source(1 to 3).runWith(Sink.fromSubscriber(s)))
          .runWith(Sink.fold[Int, Int](0)(_ + _))

      Await.result(f, 3.seconds) should be(6)
    }
  }

}

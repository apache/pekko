/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.ClosedShape
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils._
import pekko.stream.testkit.scaladsl.StreamTestKit._

class FlowPublisherSinkSpec extends StreamSpec {

  "A FlowPublisherSink" must {

    "work with SubscriberSource" in {
      val (sub, pub) =
        JavaFlowSupport.Source.asSubscriber[Int].toMat(JavaFlowSupport.Sink.asPublisher(false))(Keep.both).run()
      Source(1 to 100).to(JavaFlowSupport.Sink.fromSubscriber(sub)).run()
      Await.result(JavaFlowSupport.Source.fromPublisher(pub).limit(1000).runWith(Sink.seq), 3.seconds) should ===(
        1 to 100)
    }

    "be able to use Publisher in materialized value transformation" in {
      val f = Source(1 to 3).runWith(
        JavaFlowSupport.Sink.asPublisher[Int](false).mapMaterializedValue { p =>
          JavaFlowSupport.Source.fromPublisher(p).runFold(0)(_ + _)
        })

      Await.result(f, 3.seconds) should be(6)
    }
  }

}

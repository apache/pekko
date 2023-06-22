/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.stream.ActorAttributes._
import pekko.stream.Supervision._
import pekko.stream.testkit._
import pekko.stream.testkit.Utils._
import pekko.stream.testkit.scaladsl.TestSink

class FlowDropWhileSpec extends StreamSpec {

  "A DropWhile" must {

    "drop while predicate is true" in {
      Source(1 to 4).dropWhile(_ < 3).runWith(TestSink.probe[Int]).request(2).expectNext(3, 4).expectComplete()
    }

    "complete the future for an empty stream" in {
      Source.empty[Int].dropWhile(_ < 2).runWith(TestSink.probe[Int]).request(1).expectComplete()
    }

    "continue if error" in {
      Source(1 to 4)
        .dropWhile(a => if (a < 3) true else throw TE(""))
        .withAttributes(supervisionStrategy(resumingDecider))
        .runWith(TestSink.probe[Int])
        .request(1)
        .expectComplete()
    }

    "restart with strategy" in {
      Source(1 to 4)
        .dropWhile {
          case 1 | 3 => true
          case 4     => false
          case 2     => throw TE("")
        }
        .withAttributes(supervisionStrategy(restartingDecider))
        .runWith(TestSink.probe[Int])
        .request(1)
        .expectNext(4)
        .expectComplete()
    }

  }

}

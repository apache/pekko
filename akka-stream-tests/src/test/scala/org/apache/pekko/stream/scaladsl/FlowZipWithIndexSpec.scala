/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.stream.{ ActorMaterializer, ActorMaterializerSettings, Materializer }
import pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import scala.annotation.nowarn

@nowarn // keep unused imports
class FlowZipWithIndexSpec extends StreamSpec {

//#zip-with-index
  import org.apache.pekko
  import pekko.stream.scaladsl.Source
  import pekko.stream.scaladsl.Sink

//#zip-with-index
  val settings = ActorMaterializerSettings(system).withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer: Materializer = ActorMaterializer(settings)

  "A ZipWithIndex for Flow " must {

    "work in the happy case" in {
      val probe = TestSubscriber.manualProbe[(Int, Long)]()
      Source(7 to 10).zipWithIndex.runWith(Sink.fromSubscriber(probe))
      val subscription = probe.expectSubscription()

      subscription.request(2)
      probe.expectNext((7, 0L))
      probe.expectNext((8, 1L))

      subscription.request(1)
      probe.expectNext((9, 2L))
      subscription.request(1)
      probe.expectNext((10, 3L))

      probe.expectComplete()
    }

    "work in fruit example" in {
      // #zip-with-index
      Source(List("apple", "orange", "banana")).zipWithIndex.runWith(Sink.foreach(println))
      // this will print ('apple', 0), ('orange', 1), ('banana', 2)
      // #zip-with-index
    }

  }
}

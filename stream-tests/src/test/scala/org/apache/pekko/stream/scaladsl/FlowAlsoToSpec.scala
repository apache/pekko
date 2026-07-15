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

package org.apache.pekko.stream.scaladsl

import scala.concurrent.duration._

import org.apache.pekko

import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource

class FlowAlsoToSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "alsoTo with propagateCancellation=true (default)" must {

    "cancel the stream when side sink cancels" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink).runWith(mainSink)

      mainProbe.request(2)
      sideProbe.request(2)

      pub.sendNext(1)
      mainProbe.expectNext(1)
      sideProbe.expectNext(1)

      sideProbe.cancel()
      pub.expectCancellation()
    }

    "forward elements to both downstreams" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink).runWith(mainSink)

      mainProbe.request(3)
      sideProbe.request(3)

      pub.sendNext(1)
      pub.sendNext(2)
      pub.sendNext(3)

      mainProbe.expectNext(1, 2, 3)
      sideProbe.expectNext(1, 2, 3)

      pub.sendComplete()
      mainProbe.expectComplete()
      sideProbe.expectComplete()
    }
  }

  "alsoTo with propagateCancellation=false" must {

    "continue main stream when side sink cancels" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(4)
      sideProbe.request(2)

      pub.sendNext(1)
      mainProbe.expectNext(1)
      sideProbe.expectNext(1)

      pub.sendNext(2)
      mainProbe.expectNext(2)
      sideProbe.expectNext(2)

      sideProbe.cancel()

      pub.sendNext(3)
      mainProbe.expectNext(3)

      pub.sendNext(4)
      mainProbe.expectNext(4)

      pub.sendComplete()
      mainProbe.expectComplete()
    }

    "continue main stream when side sink fails" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      val failingSideSink = Flow[Int].map { elem =>
        if (elem == 1) throw new RuntimeException("side sink failure")
        elem
      }.to(sideSink)

      src.alsoTo(failingSideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(3)
      sideProbe.request(3)

      pub.sendNext(1)
      mainProbe.expectNext(1)

      pub.sendNext(2)
      mainProbe.expectNext(2)

      pub.sendNext(3)
      mainProbe.expectNext(3)

      pub.sendComplete()
      mainProbe.expectComplete()
    }

    "cancel side sink when main downstream cancels" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(1)
      sideProbe.request(1)

      pub.sendNext(1)
      mainProbe.expectNext(1)
      sideProbe.expectNext(1)

      mainProbe.cancel()
      pub.expectCancellation()
    }

    "forward elements to both downstreams before side cancels" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(3)
      sideProbe.request(3)

      pub.sendNext(1)
      pub.sendNext(2)
      pub.sendNext(3)

      mainProbe.expectNext(1, 2, 3)
      sideProbe.expectNext(1, 2, 3)

      pub.sendComplete()
      mainProbe.expectComplete()
      sideProbe.expectComplete()
    }

    "complete normally when upstream completes" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(2)
      sideProbe.request(2)

      pub.sendNext(1)
      pub.sendNext(2)
      pub.sendComplete()

      mainProbe.expectNext(1, 2).expectComplete()
      sideProbe.expectNext(1, 2).expectComplete()
    }

    "handle side sink cancelling before any element is emitted" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(2)
      sideProbe.request(1)

      sideProbe.cancel()

      pub.sendNext(1)
      mainProbe.expectNext(1)

      pub.sendNext(2)
      mainProbe.expectNext(2)

      pub.sendComplete()
      mainProbe.expectComplete()
    }

    "propagate upstream failure to both downstreams" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(1)
      sideProbe.request(1)

      val ex = new RuntimeException("upstream boom")
      pub.sendError(ex)

      mainProbe.expectError(ex)
      sideProbe.expectError(ex)
    }

    "backpressure when side sink is slow" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(3)
      sideProbe.request(1)

      pub.sendNext(1)
      mainProbe.expectNext(1)
      sideProbe.expectNext(1)

      sideProbe.request(1)

      pub.sendNext(2)
      mainProbe.expectNext(2)
      sideProbe.expectNext(2)

      sideProbe.request(1)

      pub.sendNext(3)
      mainProbe.expectNext(3)
      sideProbe.expectNext(3)

      pub.sendComplete()
      mainProbe.expectComplete()
      sideProbe.expectComplete()
    }

    "handle side sink cancelling while pending element exists" in {
      val (mainProbe, mainSink) = TestSink[Int]().preMaterialize()
      val (sideProbe, sideSink) = TestSink[Int]().preMaterialize()
      val (pub, src) = TestSource[Int]().preMaterialize()

      src.alsoTo(sideSink, propagateCancellation = false).runWith(mainSink)

      mainProbe.request(3)
      sideProbe.request(1)

      pub.sendNext(1)
      mainProbe.expectNext(1)
      sideProbe.expectNext(1)

      pub.sendNext(2)

      sideProbe.cancel()
      mainProbe.expectNext(2)

      pub.sendNext(3)
      mainProbe.expectNext(3)

      pub.sendComplete()
      mainProbe.expectComplete()
    }
  }
}

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

import org.apache.pekko
import pekko.stream.{ Attributes, StreamSubscriptionTimeoutTerminationMode }
import pekko.stream.ActorAttributes.StreamSubscriptionTimeout
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }

class SourceSinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "Sink.toSeq" must {
    "Can be used as a Source with run twice" in {
      val s = Source(1 to 6).runWith(Sink.source)
      s.runWith(Sink.seq).futureValue should be(1 to 6)
    }

    "Can complete when upstream completes without elements" in {
      val s = Source.empty.runWith(Sink.source)
      s.runWith(Sink.seq).futureValue should be(Nil)
    }

    "Can cancel when down stream cancel" in {
      val (pub, source) = TestSource.probe[Int]
        .toMat(Sink.source)(Keep.both)
        .run()
      val sub = source.runWith(TestSink.probe[Int])
      pub.ensureSubscription()
      sub.ensureSubscription()
      sub.cancel()
      pub.expectCancellation()
    }

    "Can timeout when no subscription" in {
      import scala.concurrent.duration._
      val (pub, source) = TestSource.probe[Int]
        .toMat(Sink.source)(Keep.both)
        .addAttributes(Attributes(
          StreamSubscriptionTimeout(
            2.seconds,
            StreamSubscriptionTimeoutTerminationMode.cancel
          )
        ))
        .run()
      pub.expectCancellation()
      Thread.sleep(1000) // wait a bit
      val sub = source.runWith(TestSink.probe)
      sub.expectSubscription()
      sub.expectError()
    }

    "Can backpressure" in {
      Source.iterate(1)(_ => true, _ + 1)
        .runWith(Sink.source).runWith(TestSink.probe[Int])
        .request(3)
        .expectNext(1, 2, 3)
        .request(2)
        .expectNext(4, 5)
        .cancel()
    }

    "Can use with mapMaterializedValue" in {
      val sink = Sink.source[Int].mapMaterializedValue(_.runWith(Sink.seq))
      Source(1 to 5)
        .runWith(sink)
        .futureValue should be(1 to 5)
    }
  }
}

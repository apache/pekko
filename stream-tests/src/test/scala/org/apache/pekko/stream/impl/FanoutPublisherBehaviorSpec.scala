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

package org.apache.pekko.stream.impl

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.testkit.EventFilter
import pekko.stream.Attributes
import pekko.stream.ActorAttributes
import pekko.stream.StreamSubscriptionTimeoutTerminationMode
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.Utils.TE

class FanoutPublisherBehaviorSpec extends StreamSpec {

  "Sink.asPublisher(fanout = true)" must {

    "surface upstream failure to late subscribers without subscriptions" in {
      val boom = TE("boom")
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      promise.failure(boom)

      Source.fromPublisher(publisher).runWith(Sink.ignore).failed.futureValue shouldBe boom
    }

    "propagate upstream failure to active and late subscribers" in {
      val boom = TE("boom")
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val running = Source.fromPublisher(publisher).runWith(Sink.ignore)
      promise.failure(boom)

      running.failed.futureValue shouldBe boom
      Source.fromPublisher(publisher).runWith(Sink.ignore).failed.futureValue shouldBe boom
    }

    "propagate upstream failure to all active subscribers" in {
      val boom = TE("boom")
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val running1 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      val running2 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      promise.failure(boom)

      running1.failed.futureValue shouldBe boom
      running2.failed.futureValue shouldBe boom
      Source.fromPublisher(publisher).runWith(Sink.ignore).failed.futureValue shouldBe boom
    }

    "complete late subscribers after completed upstream without subscriptions" in {
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      promise.success(None)

      Source.fromPublisher(publisher).runWith(Sink.seq).futureValue should ===(Nil)
    }

    "complete active and late subscribers after completed upstream" in {
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val completed = Source.fromPublisher(publisher).runWith(Sink.ignore)

      promise.success(None)

      completed.futureValue
      Source.fromPublisher(publisher).runWith(Sink.seq).futureValue should ===(Nil)
    }

    "complete multiple active subscribers after completed upstream" in {
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val completed1 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      val completed2 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      promise.success(None)

      completed1.futureValue
      completed2.futureValue
      Source.fromPublisher(publisher).runWith(Sink.seq).futureValue should ===(Nil)
    }

    "keep remaining subscribers running when one of multiple subscriptions cancels before upstream completes" in {
      val upstream = TestPublisher.probe[Int]()
      val publisher = Source
        .fromPublisher(upstream)
        .runWith(Sink.asPublisher(true).withAttributes(Attributes.inputBuffer(1, 1)))
      val cancellingSubscriber = Source.fromPublisher(publisher).runWith(TestSink[Int]())
      val remainingSubscriber = Source.fromPublisher(publisher).runWith(TestSink[Int]())

      val cancellingSubscription = cancellingSubscriber.ensureSubscription()
      val remainingSubscription = remainingSubscriber.ensureSubscription()

      upstream.expectRequest()
      remainingSubscription.request(2)
      cancellingSubscription.cancel()

      upstream.sendNext(1)
      remainingSubscriber.expectNext(1)
      upstream.expectRequest()
      upstream.sendNext(2)
      remainingSubscriber.expectNext(2)
      upstream.sendComplete()
      remainingSubscriber.expectComplete()
      Source.fromPublisher(publisher).runWith(Sink.seq).futureValue should ===(Nil)
    }

    "deliver the same elements to concurrent active subscribers" in {
      val upstream = TestPublisher.probe[Int]()
      val publisher = Source
        .fromPublisher(upstream)
        .runWith(Sink.asPublisher(true).withAttributes(Attributes.inputBuffer(4, 4)))
      val subscriber1 = Source.fromPublisher(publisher).runWith(TestSink[Int]())
      val subscriber2 = Source.fromPublisher(publisher).runWith(TestSink[Int]())

      subscriber1.ensureSubscription().request(3)
      subscriber2.ensureSubscription().request(3)

      upstream.expectRequest()
      upstream.sendNext(1)
      upstream.sendNext(2)
      upstream.sendNext(3)

      subscriber1.expectNext(1)
      subscriber1.expectNext(2)
      subscriber1.expectNext(3)
      subscriber2.expectNext(1)
      subscriber2.expectNext(2)
      subscriber2.expectNext(3)

      upstream.sendComplete()
      subscriber1.expectComplete()
      subscriber2.expectComplete()
    }

    "shut down late subscribers normally after buffered active subscribers drain a completed upstream" in {
      val upstream = TestPublisher.probe[Int]()
      val publisher = Source
        .fromPublisher(upstream)
        .runWith(Sink.asPublisher(true).withAttributes(Attributes.inputBuffer(4, 4)))
      val fastSubscriber = Source.fromPublisher(publisher).runWith(TestSink[Int]())
      val slowSubscriber = Source.fromPublisher(publisher).runWith(TestSink[Int]())

      fastSubscriber.ensureSubscription().request(2)
      val slowSubscription = slowSubscriber.ensureSubscription()

      upstream.expectRequest()
      upstream.sendNext(1)
      upstream.sendNext(2)
      upstream.sendComplete()

      fastSubscriber.expectNext(1)
      fastSubscriber.expectNext(2)
      fastSubscriber.expectComplete()

      slowSubscription.request(2)
      slowSubscriber.expectNext(1)
      slowSubscriber.expectNext(2)
      slowSubscriber.expectComplete()

      Source.fromPublisher(publisher).runWith(Sink.head).failed.futureValue should
      ===(ActorPublisher.NormalShutdownReason)
    }

    "shut down normally when the last subscriber fails downstream" in {
      val upstream = TestPublisher.probe[Int]()
      val publisher = Source.fromPublisher(upstream).runWith(Sink.asPublisher(true))
      val boom = TE("boom")

      val failed = Source.fromPublisher(publisher).map(_ => throw boom).runWith(Sink.ignore)
      upstream.expectRequest()
      upstream.sendNext(1)
      failed.failed.futureValue shouldBe boom
      upstream.expectCancellation()
      Source.fromPublisher(publisher).runWith(Sink.head).failed.futureValue should
      ===(ActorPublisher.NormalShutdownReason)
    }

    "fail with SubscriptionTimeoutException on subscriber timeout" in {
      val shortTimeout = 300.millis
      val timeoutAttributes = ActorAttributes.streamSubscriptionTimeout(
        shortTimeout, StreamSubscriptionTimeoutTerminationMode.CancelTermination)
      val upstream = TestPublisher.probe[Int]()
      val publisher = Source.fromPublisher(upstream).runWith(Sink.asPublisher(true).addAttributes(timeoutAttributes))
      upstream.expectCancellation()

      val result = Source.fromPublisher(publisher).runWith(Sink.head)
      val ex = intercept[SubscriptionTimeoutException] {
        Await.result(result, 3.seconds)
      }
      ex.getMessage should include("Subscription timeout expired")
    }

    "continue accepting subscribers after WarnTermination timeout" in {
      val shortTimeout = 300.millis
      val timeoutAttributes = ActorAttributes.streamSubscriptionTimeout(
        shortTimeout, StreamSubscriptionTimeoutTerminationMode.WarnTermination)
      val upstream = TestPublisher.probe[Int]()
      val publisher = EventFilter.warning(start = "Subscription timeout for", occurrences = 1).intercept {
        Source.fromPublisher(upstream).runWith(Sink.asPublisher(true).addAttributes(timeoutAttributes))
      }

      val subscriber = Source.fromPublisher(publisher).runWith(TestSink[Int]())
      subscriber.ensureSubscription().request(1)
      upstream.expectRequest()
      upstream.sendNext(42)
      subscriber.expectNext(42)
      upstream.sendComplete()
      subscriber.expectComplete()
    }

  }

}

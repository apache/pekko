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
import pekko.Done
import pekko.stream.testkit._

class FlowDoOnCancelSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A DoOnCancel" must {
    "be invoked once on normal cancellation" in {
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      val promise = scala.concurrent.Promise[Done]()
      val f: (Throwable, Boolean) => Unit = (_, wasCancelledNormally) => {
        if (wasCancelledNormally) invoked.incrementAndGet()
        promise.success(Done)
      }
      Source(1 to 10)
        .watchTermination()(Keep.right)
        .via(Flow[Int].doOnCancel(f))
        .toMat(Sink.cancelled)(Keep.left)
        .run()
        .futureValue
        .shouldBe(Done)
      promise.future.futureValue.shouldBe(Done)
      invoked.get() shouldBe 1
    }

    "be invoked once on stream completed" in {
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      val promise = scala.concurrent.Promise[Done]()
      val f: (Throwable, Boolean) => Unit = (_, wasCancelledNormally) => {
        if (wasCancelledNormally) invoked.incrementAndGet()
        promise.success(Done)
      }
      Source(1 to 10)
        .via(Flow[Int].doOnCancel(f))
        .take(1)
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      promise.future.futureValue.shouldBe(Done)
      invoked.get() shouldBe 1
    }

    "Not be invoked on empty source" in {
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      val f: (Throwable, Boolean) => Unit = (_, wasCancelledNormally) => {
        if (wasCancelledNormally) invoked.incrementAndGet()
      }
      Source.empty
        .via(Flow[Int].doOnCancel(f))
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      invoked.get() shouldBe 0
    }

    "be invoked once on downstream cancellation with no more elements needed" in {
      import org.apache.pekko.stream.SubscriptionWithCancelException._
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      val promise = scala.concurrent.Promise[Done]()
      val f: (Throwable, Boolean) => Unit = (cause, wasCancelledNormally) => {
        if (wasCancelledNormally && (cause eq NoMoreElementsNeeded)) invoked.incrementAndGet()
        promise.success(Done)
      }
      Source(1 to 10)
        .orElse(Source.never[Int].via(Flow[Int].doOnCancel(f)))
        .take(5)
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      promise.future.futureValue.shouldBe(Done)
      invoked.get() shouldBe 1
    }

    "be invoked once on downstream cancellation on failure" in {
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      val promise = scala.concurrent.Promise[Done]()
      val f: (Throwable, Boolean) => Unit = (_, wasCancelledNormally) => {
        if (!wasCancelledNormally) invoked.incrementAndGet()
        promise.success(Done)
      }
      Source(1 to 10)
        .via(Flow[Int].doOnCancel(f))
        .map {
          case 5 => throw new RuntimeException("Boom!")
          case x => x
        }
        .runWith(Sink.ignore)
        .failed
        .futureValue
        .shouldBe(an[RuntimeException])
      promise.future.futureValue.shouldBe(Done)
      invoked.get() shouldBe 1
    }

  }

}

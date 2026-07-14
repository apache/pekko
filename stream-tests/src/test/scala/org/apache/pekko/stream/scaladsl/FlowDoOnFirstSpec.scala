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

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko

import pekko.Done
import pekko.stream.{ ActorAttributes, Supervision }
import pekko.stream.testkit._

class FlowDoOnFirstSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A DoOnFirst" must {

    "can only invoke on first" in {
      val invoked = new AtomicInteger(0)
      Source(1 to 10)
        .via(Flow[Int].doOnFirst(invoked.addAndGet))
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      invoked.get() shouldBe 1
    }

    "will not invoke on empty stream" in {
      val invoked = new AtomicInteger(0)
      Source.empty
        .via(Flow[Int].doOnFirst(invoked.addAndGet))
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      invoked.get() shouldBe 0
    }

    "fail the stream when f throws and supervision is Stop" in {
      val ex = new RuntimeException("doOnFirst-stop")
      val result = Source(1 to 5)
        .via(Flow[Int].doOnFirst(_ => throw ex))
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
        .runWith(Sink.ignore)

      result.failed.futureValue shouldBe ex
    }

    "fail the stream when f throws and no supervision attribute is set (default Stop)" in {
      val ex = new RuntimeException("doOnFirst-default")
      val result = Source(1 to 5)
        .via(Flow[Int].doOnFirst(_ => throw ex))
        .runWith(Sink.ignore)

      result.failed.futureValue shouldBe ex
    }

    "resume drops the first element, does not retry f, and passes the rest through" in {
      val invocationCount = new AtomicInteger(0)
      val ex = new RuntimeException("doOnFirst-resume")

      val result = Source(1 to 5)
        .via(Flow[Int].doOnFirst { _ =>
          if (invocationCount.incrementAndGet() == 1) throw ex
        })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runWith(Sink.seq)
        .futureValue

      result shouldBe Seq(2, 3, 4, 5)
      invocationCount.get() shouldBe 1
    }

    "restart re-arms so f is retried on the next element" in {
      val invocationCount = new AtomicInteger(0)
      val ex = new RuntimeException("doOnFirst-restart")

      val result = Source(1 to 3)
        .via(Flow[Int].doOnFirst { _ =>
          invocationCount.incrementAndGet()
          throw ex
        })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(Sink.seq)
        .futureValue

      result shouldBe Seq.empty
      invocationCount.get() shouldBe 3
    }

    "restart and continue with pass-through once f succeeds on retry" in {
      // Element 1 triggers f which throws -> Restart drops element 1 and re-arms.
      // Element 2 triggers f which succeeds -> handler switches to pass-through.
      // Elements 3 and 4 pass through without invoking f.
      val invocationCount = new AtomicInteger(0)
      val ex = new RuntimeException("doOnFirst-restart-then-ok")

      val result = Source(1 to 4)
        .via(Flow[Int].doOnFirst { _ =>
          if (invocationCount.incrementAndGet() == 1) throw ex
        })
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider))
        .runWith(Sink.seq)
        .futureValue

      result shouldBe Seq(2, 3, 4)
      invocationCount.get() shouldBe 2
    }

  }

}

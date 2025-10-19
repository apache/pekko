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

import org.apache.pekko.Done
import org.apache.pekko.stream.testkit._

class FlowDoOnFirstSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A DoOnFirst" must {

    "can only invoke on first" in {
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      Source(1 to 10)
        .via(Flow[Int].doOnFirst(invoked.addAndGet))
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      invoked.get() shouldBe 1
    }

    "will not invoke on empty stream" in {
      val invoked = new java.util.concurrent.atomic.AtomicInteger(0)
      Source.empty
        .via(Flow[Int].doOnFirst(invoked.addAndGet))
        .runWith(Sink.ignore)
        .futureValue
        .shouldBe(Done)
      invoked.get() shouldBe 0
    }

  }

}

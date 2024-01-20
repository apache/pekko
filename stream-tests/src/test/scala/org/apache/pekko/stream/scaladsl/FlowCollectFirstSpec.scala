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
import pekko.stream.ActorAttributes._
import pekko.stream.OverflowStrategy
import pekko.stream.Supervision._
import pekko.stream.testkit.Utils.TE
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.{ ScriptedTest, StreamSpec }

class FlowCollectFirstSpec extends StreamSpec with ScriptedTest {

  "A CollectFirst" must {

    "collect in happy path" in {
      Source(List(1, 3, 5, 7, 8, 9, 10))
        .collectFirst {
          case elem if elem % 2 == 0 => elem
        }
        .runWith(TestSink())
        .request(7)
        .expectNext(8)
        .expectComplete()
    }

    "complete with buffer even no explict request" in {
      Source(List(2, 4, 6))
        .collectFirst {
          case elem if elem % 2 != 0 => elem
        }
        .buffer(1, overflowStrategy = OverflowStrategy.backpressure)
        .runWith(TestSink())
        .ensureSubscription()
        .expectComplete()
    }

    "complete with empty Source" in {
      Source.empty[Int].collectFirst {
        case elem if elem % 2 != 0 => elem
      }.runWith(TestSink[Int]())
        .ensureSubscription()
        .expectComplete()
    }

    "restart when pf throws" in {
      Source(1 to 6)
        .collectFirst { case x: Int => if (x % 2 != 0) throw TE("") else x }
        .withAttributes(supervisionStrategy(restartingDecider))
        .runWith(TestSink[Int]())
        .request(1)
        .expectNext(2)
        .request(1)
        .expectComplete()
    }

    "resume when pf throws" in {
      Source(1 to 6)
        .collectFirst { case x: Int => if (x % 2 != 0) throw TE("") else x }
        .withAttributes(supervisionStrategy(resumingDecider))
        .runWith(TestSink[Int]())
        .request(1)
        .expectNext(2)
        .request(1)
        .expectComplete()
    }
  }

}

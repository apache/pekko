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
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.{ ScriptedTest, StreamSpec }

class FlowGroupedAdjacentByWeightedSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A GroupedAdjacentByWeighted" must {
    "produce no group when source is empty" in {
      Source.empty[String]
        .groupedAdjacentBy(identity(_))
        .runWith(TestSink.probe[Seq[String]])
        .request(1)
        .expectComplete()
    }

    "group adjacent elements by predicate" in {
      val input = List("a", "a", "b", "b", "c", "c")
      Source(input)
        .groupedAdjacentBy(identity(_))
        .runWith(TestSink.probe[Seq[String]])
        .request(6)
        .expectNext(Seq("a", "a"))
        .expectNext(Seq("b", "b"))
        .expectNext(Seq("c", "c"))
        .expectComplete()
    }

    "group adjust elements by leading char" in {
      val input = List("Hello", "Hi", "Greetings", "Hey")
      Source(input)
        .groupedAdjacentBy(_.head)
        .runWith(TestSink.probe[Seq[String]])
        .request(4)
        .expectNext(Seq("Hello", "Hi"))
        .expectNext(Seq("Greetings"))
        .expectNext(Seq("Hey"))
        .expectComplete()
    }

    "be able to act like bufferUntilChanged" in {
      Source(List(1, 1, 2, 2, 3, 3, 1))
        .groupedAdjacentBy(identity(_))
        .runWith(TestSink.probe[Seq[Int]])
        .request(7)
        .expectNext(Seq(1, 1))
        .expectNext(Seq(2, 2))
        .expectNext(Seq(3, 3))
        .expectNext(Seq(1))
        .expectComplete()
    }

    "Be able to limit the chunk size" in {
      Source(List("Hello", "Hi", "Hey", "Greetings", "Hey"))
        .groupedAdjacentByWeighted(_.head, 2)(_ => 1L)
        .runWith(TestSink.probe[Seq[String]])
        .request(5)
        .expectNext(Seq("Hello", "Hi"))
        .expectNext(Seq("Hey"))
        .expectNext(Seq("Greetings"))
        .expectNext(Seq("Hey"))
        .expectComplete()
    }

    "Be able to handle single heavy weighted element" in {
      Source(List("Hello", "HiHi", "Hi", "Hi", "Greetings", "Hey"))
        .groupedAdjacentByWeighted(_.head, 4)(_.length)
        .runWith(TestSink.probe[Seq[String]])
        .request(6)
        .expectNext(Seq("Hello"))
        .expectNext(Seq("HiHi"))
        .expectNext(Seq("Hi", "Hi"))
        .expectNext(Seq("Greetings"))
        .expectNext(Seq("Hey"))
        .expectComplete()
    }

  }

}

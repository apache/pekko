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

import java.util.concurrent.CopyOnWriteArrayList

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.apache.pekko
import pekko.Done
import pekko.japi.Util
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink

class ForComprehensionsCompileSpec extends StreamSpec {
  "A Source" must {
    "be able to be used in a for comprehension which yield" in {
      val source = Source(1 to 5)
      val evenSource = for {
        i <- source if i % 2 == 0
      } yield i.toString
      evenSource.runWith(TestSink[String]())
        .request(5)
        .expectNextN(List("2", "4"))
        .expectComplete()
    }

    "be able to be used in a for comprehension which flatMap" in {
      val source = Source(1 to 5)
      val evenSource = for {
        i <- source if i % 2 == 0
        j <- Source.lazySingle(() => i)
        str = j.toString
      } yield str
      evenSource.runWith(TestSink[String]())
        .request(5)
        .expectNextN(List("2", "4"))
        .expectComplete()
    }

    "be able to be used in a for comprehension which yield a runnable graph" in {
      val source = Source(1 to 5)
      val list = new CopyOnWriteArrayList[String]()
      val future = (for (i <- source if i % 2 == 0) {
        list.add(i.toString)
      }).run()

      Await.result(future, 3.seconds) shouldBe Done
      Util.immutableSeq(list) shouldBe List("2", "4")
    }

    "be able to be used in a for comprehension which with Flow" in {
      (for {
        i <- Source(1 to 20) if i % 2 == 0
        j <- Source.lazySingle(() => i)
        str = j.toString
      } yield str)
        .via(for {
          str <- Flow[String] if str.length > 1
          doubleStr = str + str
          number <- Source.lazySingle(() => doubleStr)
        } yield number.toInt)
        .runWith(TestSink[Int]())
        .request(6)
        .expectNextN(List(1010, 1212, 1414, 1616, 1818, 2020))
        .expectComplete()
    }
  }

  "A Flow" must {
    "be able to be used in a for comprehension which yield" in {
      Source(1 to 5).via(for (i <- Flow[Int] if i % 2 == 0) yield i.toString)
        .runWith(TestSink[String]())
        .request(5)
        .expectNextN(List("2", "4"))
        .expectComplete()
    }

    "be able to be used in a for comprehension which flatmap" in {
      Source(1 to 5).via(for {
        i <- Flow[Int] if i % 2 == 0
        j <- Source.single(i)
        str = j.toString
      } yield str)
        .runWith(TestSink[String]())
        .request(5)
        .expectNextN(List("2", "4"))
        .expectComplete()
    }

    "be able to be used in a for comprehension which yield a sink" in {
      val source = Source(1 to 5)
      val list = new CopyOnWriteArrayList[String]()
      val sink = for (i <- Flow[Int] if i % 2 == 0) {
        list.add(i.toString)
      }
      val future = source.runWith(sink)
      Await.result(future, 3.seconds) shouldBe Done
      Util.immutableSeq(list) shouldBe List("2", "4")
    }
  }
}

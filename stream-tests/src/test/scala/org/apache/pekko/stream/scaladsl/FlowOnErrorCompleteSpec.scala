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

import scala.concurrent.TimeoutException
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink

class FlowOnErrorCompleteSpec extends StreamSpec {
  val ex = new RuntimeException("ex") with NoStackTrace

  "A CompleteOn" must {
    "can complete with all exceptions" in {
      Source(List(1, 2))
        .map { a =>
          if (a == 2) throw ex else a
        }
        .onErrorComplete[Throwable]()
        .runWith(TestSink[Int]())
        .request(2)
        .expectNext(1)
        .expectComplete()
    }

    "can complete with dedicated exception type" in {
      Source(List(1, 2))
        .map { a =>
          if (a == 2) throw new IllegalArgumentException() else a
        }
        .onErrorComplete[IllegalArgumentException]()
        .runWith(TestSink[Int]())
        .request(2)
        .expectNext(1)
        .expectComplete()
    }

    "can fail if an unexpected exception occur" in {
      Source(List(1, 2))
        .map { a =>
          if (a == 2) throw new IllegalArgumentException() else a
        }
        .onErrorComplete[TimeoutException]()
        .runWith(TestSink[Int]())
        .request(1)
        .expectNext(1)
        .request(1)
        .expectError()
    }

    "can complete if the pf is applied" in {
      Source(List(1, 2))
        .map { a =>
          if (a == 2) throw new TimeoutException() else a
        }
        .onErrorComplete {
          case _: IllegalArgumentException => false
          case _: TimeoutException         => true
        }
        .runWith(TestSink[Int]())
        .request(2)
        .expectNext(1)
        .expectComplete()
    }

    "can fail if the pf is not applied" in {
      Source(List(1, 2))
        .map { a =>
          if (a == 2) throw ex else a
        }
        .onErrorComplete {
          case _: IllegalArgumentException => false
          case _: TimeoutException         => true
        }
        .runWith(TestSink[Int]())
        .request(2)
        .expectNext(1)
        .expectError()
    }

  }
}

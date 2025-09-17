/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit

import java.util
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.TestEvent.{ Mute, UnMute }
import pekko.testkit._

class StreamTestKitSpec extends PekkoSpec {

  val ex = new Exception("Boom!")

  "A TestSink Probe" must {
    "#toStrict" in {
      Source(1 to 4).runWith(TestSink.probe).toStrict(remainingOrDefault) should ===(List(1, 2, 3, 4))
    }

    "#toStrict with failing source" in {
      system.eventStream.publish(Mute(EventFilter[Exception]()))
      try {
        val error = intercept[AssertionError] {
          Source
            .fromIterator(() =>
              new Iterator[Int] {
                var i = 0

                override def hasNext: Boolean = true

                override def next(): Int = {
                  i += 1
                  i match {
                    case 3 => throw ex
                    case n => n
                  }
                }
              })
            .runWith(TestSink.probe)
            .toStrict(remainingOrDefault)
        }

        error.getMessage should startWith("toStrict received OnError")
        error.getMessage should include("List(1, 2)")
        error.getCause should ===(ex)
      } finally {
        system.eventStream.publish(UnMute(EventFilter[Exception]()))
      }
    }

    "#toStrict when subscription was already obtained" in {
      val p = Source(1 to 4).runWith(TestSink.probe)
      p.expectSubscription()
      p.toStrict(remainingOrDefault) should ===(List(1, 2, 3, 4))
    }

    "#expectNextOrError with right element" in {
      Source(1 to 4).runWith(TestSink.probe).request(4).expectNextOrError(1, ex)
    }

    "#expectNextOrError with right exception" in {
      Source.failed[Int](ex).runWith(TestSink.probe).request(4).expectNextOrError(1, ex)
    }

    "#expectNextOrError fail if the next element is not the expected one" in {
      intercept[AssertionError] {
        Source(1 to 4).runWith(TestSink.probe).request(4).expectNextOrError(100, ex)
      }.getMessage should include("OnNext(1)")
    }

    "#expectError" in {
      Source.failed[Int](ex).runWith(TestSink.probe).request(1).expectError() should ===(ex)
    }

    "#expectError fail if no error signalled" in {
      intercept[AssertionError] {
        Source(1 to 4).runWith(TestSink.probe).request(1).expectError()
      }.getMessage should include("OnNext")
    }

    "#expectComplete should fail if error signalled" in {
      intercept[AssertionError] {
        Source.failed[Int](ex).runWith(TestSink.probe).request(1).expectComplete()
      }.getMessage should include("OnError")
    }

    "#expectComplete should fail if next element signalled" in {
      intercept[AssertionError] {
        Source(1 to 4).runWith(TestSink.probe).request(1).expectComplete()
      }.getMessage should include("OnNext")
    }

    "#expectNextOrComplete with right element" in {
      Source(1 to 4).runWith(TestSink.probe).request(4).expectNextOrComplete(1)
    }

    "#expectNextOrComplete with completion" in {
      Source.single(1).runWith(TestSink.probe).request(4).expectNextOrComplete(1).expectNextOrComplete(1337)
    }

    "#expectNextPF should pass with right element" in {
      val result = Source.single(1).runWith(TestSink.probe).request(1).expectNextPF {
        case 1 => "success"
      }
      result should be("success")
    }

    "#expectNextPF should fail with wrong element" in {
      intercept[AssertionError] {
        Source.single(1).runWith(TestSink.probe).request(1).expectNextPF {
          case 2 =>
        }
      }.getMessage should include("message matching partial function")
    }

    "#expectNextWithTimeoutPF should fail after timeout when element delayed" in {
      intercept[AssertionError] {
        val timeout = 100.millis
        // Initial delay is longer than the timeout so an exception will be thrown.
        // It also needs to be dilated since the testkit will dilate the timeout
        // accordingly to `-Dpekko.test.timefactor` value.
        val initialDelay = (timeout * 2).dilated
        val pf: PartialFunction[Any, Unit] = {
          case 1 =>
            system.log.info("Message received :(")
        }
        Source
          .tick(initialDelay, 1.millis, 1)
          .runWith(TestSink.probe)
          .request(1)
          .expectNextWithTimeoutPF(timeout, pf)

      }.getMessage should include("timeout")
    }

    "#expectNextChainingPF should pass with right element" in {
      Source.single(1).runWith(TestSink.probe).request(1).expectNextChainingPF {
        case 1 =>
      }
    }

    "#expectNextChainingPF should allow to chain test methods" in {
      Source(1 to 2)
        .runWith(TestSink.probe)
        .request(2)
        .expectNextChainingPF {
          case 1 =>
        }
        .expectNext(2)
    }

    "#expectNextChainingPF should fail with wrong element" in {
      intercept[AssertionError] {
        Source.single(1).runWith(TestSink.probe).request(1).expectNextChainingPF {
          case 2 =>
        }
      }.getMessage should include("message matching partial function")
    }

    "#expectNextChainingPF should fail after timeout when element delayed" in {
      intercept[AssertionError] {
        val timeout = 100.millis
        // Initial delay is longer than the timeout so an exception will be thrown.
        // It also needs to be dilated since the testkit will dilate the timeout
        // accordingly to `-Dpekko.test.timefactor` value.
        val initialDelay = (timeout * 2).dilated
        val pf: PartialFunction[Any, Unit] = {
          case 1 =>
            system.log.info("Message received :(")
        }
        Source
          .tick(initialDelay, 1.millis, 1)
          .runWith(TestSink.probe)
          .request(1)
          .expectNextChainingPF(timeout, pf)
      }.getMessage should include("timeout")
    }

    "#expectNextN given a number of elements" in {
      Source(1 to 4).runWith(TestSink.probe).request(4).expectNextN(4) should ===(List(1, 2, 3, 4))
    }

    "#expectNextN given specific elements" in {
      Source(1 to 4).runWith(TestSink.probe).request(4).expectNextN(4) should ===(List(1, 2, 3, 4))
    }

    "#expectNextN given specific elements for java list" in {
      Source(1 to 4).runWith(TestSink[Int]())
        .request(4)
        .expectNextN(util.Arrays.asList(1, 2, 3, 4))
        .expectComplete()
    }
  }
}

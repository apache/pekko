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

package org.apache.pekko.stream.scaladsl

import scala.annotation.nowarn

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.testkit.scaladsl.StreamTestKit._
import pekko.stream.testkit.{ BaseTwoStreamsSetup, TestSubscriber }

@nowarn // keep unused imports
class FlowZipSpec extends BaseTwoStreamsSetup {

//#zip
  import org.apache.pekko

//#zip

  override type Outputs = (Int, Int)

  override def setup(p1: Publisher[Int], p2: Publisher[Int]) = {
    val subscriber = TestSubscriber.probe[Outputs]()
    Source.fromPublisher(p1).zip(Source.fromPublisher(p2)).runWith(Sink.fromSubscriber(subscriber))
    subscriber
  }

  "A Zip for Flow" must {

    "work in the happy case" in assertAllStagesStopped {
      val probe = TestSubscriber.manualProbe[(Int, String)]()
      Source(1 to 4).zip(Source(List("A", "B", "C", "D", "E", "F"))).runWith(Sink.fromSubscriber(probe))
      val subscription = probe.expectSubscription()

      subscription.request(2)
      probe.expectNext((1, "A"))
      probe.expectNext((2, "B"))

      subscription.request(1)
      probe.expectNext((3, "C"))
      subscription.request(1)
      probe.expectNext((4, "D"))

      probe.expectComplete()
    }
    commonTests()

    "work with one immediately completed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(completedPublisher, nonemptyPublisher(1 to 4))
      subscriber1.expectSubscriptionAndComplete()

      val subscriber2 = setup(nonemptyPublisher(1 to 4), completedPublisher)
      subscriber2.expectSubscriptionAndComplete()
    }

    "work with one delayed completed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(soonToCompletePublisher, nonemptyPublisher(1 to 4))
      subscriber1.expectSubscriptionAndComplete()

      val subscriber2 = setup(nonemptyPublisher(1 to 4), soonToCompletePublisher)
      subscriber2.expectSubscriptionAndComplete()
    }

    "work with one immediately failed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(failedPublisher, nonemptyPublisher(1 to 4))
      subscriber1.expectSubscriptionAndError(TestException)

      val subscriber2 = setup(nonemptyPublisher(1 to 4), failedPublisher)
      subscriber2.expectSubscriptionAndError(TestException)
    }

    "work with one delayed failed and one nonempty publisher" in assertAllStagesStopped {
      val subscriber1 = setup(soonToFailPublisher, nonemptyPublisher(1 to 4))
      subscriber1.expectSubscriptionAndError(TestException)

      val subscriber2 = setup(nonemptyPublisher(1 to 4), soonToFailPublisher)
      subscriber2.expectSubscriptionAndError(TestException)
    }

    "work in fruits example" in {
      // #zip
      val sourceFruits = Source(List("apple", "orange", "banana"))
      val sourceFirstLetters = Source(List("A", "O", "B"))
      sourceFruits.zip(sourceFirstLetters).runWith(Sink.foreach(println))
      // this will print ('apple', 'A'), ('orange', 'O'), ('banana', 'B')
      // #zip

    }
  }
}

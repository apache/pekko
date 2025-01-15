/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern

import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Scheduler
import pekko.testkit.PekkoSpec

import java.util.concurrent.atomic.AtomicInteger

class RetrySpec extends PekkoSpec with RetrySupport {
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  "pattern.retry" must {
    "run a successful Future immediately" in {
      val retried = retry(() => Future.successful(5), 5, 1.second)

      within(3.seconds) {
        Await.result(retried, remaining) should ===(5)
      }
    }

    "run a successful Future only once" in {
      @volatile var counter = 0
      val retried = retry(
        () =>
          Future.successful {
            counter += 1
            counter
          },
        5,
        1.second)

      within(3.seconds) {
        Await.result(retried, remaining) should ===(1)
      }
    }

    "eventually return a failure for a Future that will never succeed" in {
      val retried = retry(() => Future.failed(new IllegalStateException("Mexico")), 5, 100.milliseconds)

      within(3.second) {
        intercept[IllegalStateException] { Await.result(retried, remaining) }.getMessage should ===("Mexico")
      }
    }

    "return a success for a Future that succeeds eventually" in {
      @volatile var failCount = 0

      def attempt() = {
        if (failCount < 5) {
          failCount += 1
          Future.failed(new IllegalStateException(failCount.toString))
        } else Future.successful(5)
      }

      val retried = retry(() => attempt(), 10, 100.milliseconds)

      within(3.seconds) {
        Await.result(retried, remaining) should ===(5)
      }
    }

    "return a failure for a Future that would have succeeded but retires were exhausted" in {
      @volatile var failCount = 0

      def attempt() = {
        if (failCount < 10) {
          failCount += 1
          Future.failed(new IllegalStateException(failCount.toString))
        } else Future.successful(5)
      }

      val retried = retry(() => attempt(), 5, 100.milliseconds)

      within(3.seconds) {
        intercept[IllegalStateException] { Await.result(retried, remaining) }.getMessage should ===("6")
      }
    }

    "return a failure for a Future that would have succeeded but retires were exhausted with delay function" in {
      @volatile var failCount = 0
      @volatile var attemptedCount = 0;

      def attempt() = {
        if (failCount < 10) {
          failCount += 1
          Future.failed(new IllegalStateException(failCount.toString))
        } else Future.successful(5)
      }

      val retried = retry(() => attempt(), 5,
        attempted => {
          attemptedCount = attempted
          Some(100.milliseconds * attempted)
        })
      within(30000000.seconds) {
        intercept[IllegalStateException] { Await.result(retried, remaining) }.getMessage should ===("6")
        attemptedCount shouldBe 5
      }
    }

    "retry can be attempted without any delay" in {
      @volatile var failCount = 0

      def attempt() = {
        if (failCount < 1000) {
          failCount += 1
          Future.failed(new IllegalStateException(failCount.toString))
        } else Future.successful(1)
      }
      val start = System.currentTimeMillis()
      val retried = retry(() => attempt(), 999)

      within(1.seconds) {
        intercept[IllegalStateException] {
          Await.result(retried, remaining)
        }.getMessage should ===("1000")
        val elapse = System.currentTimeMillis() - start
        elapse <= 100 shouldBe true
      }
    }

    "handle thrown exceptions in same way as failed Future" in {
      @volatile var failCount = 0

      def attempt() = {
        if (failCount < 5) {
          failCount += 1
          throw new IllegalStateException(failCount.toString)
        } else Future.successful(5)
      }

      val retried = retry(() => attempt(), 10, 100.milliseconds)

      within(3.seconds) {
        Await.result(retried, remaining) should ===(5)
      }
    }

    "be able to retry with predicate for value" in {
      val counter = new AtomicInteger(0)
      def attempt(): Future[Int] = {
        Future.successful(counter.incrementAndGet())
      }

      val retried = retry(() => attempt(), (t: Int, _) => t < 5, 10, 100.milliseconds)

      within(3.seconds) {
        Await.result(retried, remaining) should ===(5)
      }
    }

    "be able to skip retry with predicate for exception" in {
      val counter = new AtomicInteger(0)

      def attempt(): Future[Int] = {
        counter.incrementAndGet()
        // should not retry on this exception
        Future.failed(new IllegalArgumentException())
      }

      val retried =
        retry(() => attempt(), (_: Int, e) => !e.isInstanceOf[IllegalArgumentException], 10, 100.milliseconds)

      within(3.seconds) {
        retried.failed.futureValue shouldBe an[IllegalArgumentException]
        counter.get() should ===(1)
      }
    }

  }

}

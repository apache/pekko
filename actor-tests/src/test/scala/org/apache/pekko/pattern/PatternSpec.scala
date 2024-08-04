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

import scala.concurrent.{ Await, ExecutionContextExecutor, Future, Promise, TimeoutException }
import scala.concurrent.duration._
import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.testkit.{ PekkoSpec, TestLatch }

object PatternSpec {
  final case class Work(duration: Duration)
  class TargetActor extends Actor {
    def receive = {
      case (testLatch: TestLatch, duration: FiniteDuration) =>
        Await.ready(testLatch, duration)
    }
  }
}

class PatternSpec extends PekkoSpec {
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  import PatternSpec._

  "pattern.gracefulStop" must {

    "provide Future for stopping an actor" in {
      val target = system.actorOf(Props[TargetActor]())
      val result = gracefulStop(target, 5.seconds)
      Await.result(result, 6.seconds) should ===(true)
    }

    "complete Future when actor already terminated" in {
      val target = system.actorOf(Props[TargetActor]())
      Await.ready(gracefulStop(target, 5.seconds), 6.seconds)
      Await.ready(gracefulStop(target, 1.millis), 1.second)
    }

    "complete Future with AskTimeoutException when actor not terminated within timeout" in {
      val target = system.actorOf(Props[TargetActor]())
      val latch = TestLatch()
      target ! ((latch, remainingOrDefault))
      intercept[AskTimeoutException] { Await.result(gracefulStop(target, 500.millis), remainingOrDefault) }
      latch.open()
    }
  }

  "pattern.after" must {
    "be completed successfully eventually" in {
      // TODO after is unfortunately shadowed by ScalaTest, fix as part of #3759
      val f = pekko.pattern.after(1.second, using = system.scheduler)(Future.successful(5))

      val r = Future.firstCompletedOf(Seq(Promise[Int]().future, f))
      Await.result(r, remainingOrDefault) should ===(5)
    }

    "be completed abnormally eventually" in {
      // TODO after is unfortunately shadowed by ScalaTest, fix as part of #3759
      val f =
        pekko.pattern.after(1.second, using = system.scheduler)(Future.failed(new IllegalStateException("Mexico")))

      val r = Future.firstCompletedOf(Seq(Promise[Int]().future, f))
      intercept[IllegalStateException] { Await.result(r, remainingOrDefault) }.getMessage should ===("Mexico")
    }
  }

  "pattern.timeout" must {
    "be completed successfully eventually" in {
      val f = pekko.pattern.timeout(100.millis, using = system.scheduler)(Future.successful(5))
      Await.result(f, remainingOrDefault) should ===(5)
    }

    "be completed abnormally eventually" in {
      val f =
        pekko.pattern.timeout(100.millis, using = system.scheduler)(Future.failed(new IllegalStateException("ABC")))
      intercept[IllegalStateException] { Await.result(f, remainingOrDefault) }.getMessage should ===("ABC")
    }

    "be completed with a TimeoutException if not completed within the specified time" in {
      val f = pekko.pattern.timeout(100.millis, using = system.scheduler)(Future.never)
      intercept[TimeoutException] { Await.result(f, remainingOrDefault) }
    }
  }

}

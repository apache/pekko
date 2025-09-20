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

package org.apache.pekko.dispatch

import scala.concurrent.ExecutionContext
import scala.concurrent.Promise

import org.apache.pekko
import pekko.Done
import pekko.testkit.PekkoSpec

import org.scalatest.matchers.should.Matchers

class SameThreadExecutionContextSpec extends PekkoSpec with Matchers {

  "The SameThreadExecutionContext" should {

    "return a Scala specific version" in {
      val ec = ExecutionContext.parasitic
      // in Scala 2.13 and higher parasitic is available
      ec.getClass.getName should ===("scala.concurrent.ExecutionContext$parasitic$")
    }

    "should run follow up future operations in the same dispatcher" in {
      // covered by the respective impl test suites for sure but just in case
      val promise = Promise[Done]()
      val futureThreadNames = promise.future
        .map { _ =>
          Thread.currentThread().getName
        }(system.dispatcher)
        .map(firstName => firstName -> Thread.currentThread().getName)(ExecutionContext.parasitic)

      promise.success(Done)
      val (threadName1, threadName2) = futureThreadNames.futureValue
      threadName1 should ===(threadName2)
    }

    "should run follow up future operations in the same execution context" in {
      // covered by the respective impl test suites for sure but just in case
      val promise = Promise[Done]()
      val futureThreadNames = promise.future
        .map { _ =>
          Thread.currentThread().getName
        }(ExecutionContext.global)
        .map(firstName => firstName -> Thread.currentThread().getName)(ExecutionContext.parasitic)

      promise.success(Done)
      val (threadName1, threadName2) = futureThreadNames.futureValue
      threadName1 should ===(threadName2)
    }

  }

}

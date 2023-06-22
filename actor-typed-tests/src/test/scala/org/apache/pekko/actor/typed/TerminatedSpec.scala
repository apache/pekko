/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.TestInbox

class TerminatedSpec extends AnyWordSpec with Matchers with LogCapturing {

  "Child Failed" must {
    "should be pattern matchable" in {

      val probe = TestInbox[String]()
      val ex = new RuntimeException("oh dear")
      val childFailed = new ChildFailed(probe.ref, ex)

      (childFailed match {
        case Terminated(r) => r
        case unexpected    => throw new RuntimeException(s"Unexpected: $unexpected")
      }) shouldEqual probe.ref

      (childFailed match {
        case ChildFailed(ref, e) => (ref, e)
        case unexpected          => throw new RuntimeException(s"Unexpected: $unexpected")
      }) shouldEqual ((probe.ref, ex))

    }

  }

}

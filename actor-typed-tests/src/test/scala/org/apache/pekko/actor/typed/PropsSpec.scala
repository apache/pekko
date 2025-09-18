/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed

import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PropsSpec extends AnyWordSpec with Matchers with LogCapturing {

  val dispatcherFirst = Props.empty.withDispatcherFromConfig("pool").withDispatcherDefault

  "A Props" must {

    "get first dispatcher" in {
      dispatcherFirst.firstOrElse[DispatcherSelector](null) should ===(dispatcherFirst)
    }

    "yield all configs of some type" in {
      dispatcherFirst.allOf[DispatcherSelector] should ===(
        DispatcherSelector.default() :: DispatcherSelector.fromConfig("pool") :: Nil)
    }
  }
}

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

package org.apache.pekko.stream.impl.fusing

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils.TE
import pekko.testkit.EventFilter

class GraphInterpreterFailureModesSpec extends StreamSpec with GraphInterpreterSpecKit {

  "GraphInterpreter" must {

    "handle failure on onPull" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(
        Set(Cancel(upstream, testException), OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onPush" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      stepAll()
      clearEvents()
      upstream.push(0)
      failOnNextEvent()
      stepAll()

      lastEvents() should be(
        Set(Cancel(upstream, testException), OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onPull while cancel is pending" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      downstream.cancel()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(Cancel(upstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onPush while complete is pending" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.pull()
      stepAll()
      clearEvents()
      upstream.push(0)
      upstream.complete()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onUpstreamFinish" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      upstream.complete()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onUpstreamFailure" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      upstream.fail(TE("another exception")) // this is not the exception that will be propagated
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure on onDownstreamFinish" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      downstream.cancel()
      failOnNextEvent()
      stepAll()

      lastEvents() should be(Set(Cancel(upstream, testException), PostStop(insideOutStage)))
    }

    "handle failure in preStart" in new FailingStageSetup(initFailOnNextEvent = true) {
      stepAll()

      lastEvents() should be(
        Set(Cancel(upstream, testException), OnError(downstream, testException), PostStop(insideOutStage)))
    }

    "handle failure in postStop" in new FailingStageSetup {
      lastEvents() should be(Set(PreStart(insideOutStage)))

      upstream.complete()
      downstream.cancel()
      failOnPostStop()

      EventFilter.error("Error during postStop in [stage]").intercept {
        stepAll()
        lastEvents() should be(Set.empty)
      }

    }

  }

}

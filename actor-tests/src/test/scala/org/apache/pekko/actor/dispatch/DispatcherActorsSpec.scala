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

package org.apache.pekko.actor.dispatch

import java.util.concurrent.CountDownLatch

import org.apache.pekko
import pekko.actor._
import pekko.testkit.PekkoSpec

/**
 * Tests the behavior of the executor based event driven dispatcher when multiple actors are being dispatched on it.
 */
class DispatcherActorsSpec extends PekkoSpec {
  class SlowActor(finishedCounter: CountDownLatch) extends Actor {

    def receive = {
      case _: Int => {
        Thread.sleep(50) // slow actor
        finishedCounter.countDown()
      }
    }
  }

  class FastActor(finishedCounter: CountDownLatch) extends Actor {
    def receive = {
      case _: Int => {
        finishedCounter.countDown()
      }
    }
  }

  "A dispatcher and two actors" must {
    "not block fast actors by slow actors" in {
      val sFinished = new CountDownLatch(50)
      val fFinished = new CountDownLatch(10)
      val s = system.actorOf(Props(new SlowActor(sFinished)))
      val f = system.actorOf(Props(new FastActor(fFinished)))

      // send a lot of stuff to s
      for (i <- 1 to 50) {
        s ! i
      }

      // send some messages to f
      for (i <- 1 to 10) {
        f ! i
      }

      // now assert that f is finished while s is still busy
      fFinished.await()
      assert(sFinished.getCount > 0)
      sFinished.await()
      assert(sFinished.getCount === 0L)
      system.stop(f)
      system.stop(s)
    }
  }
}

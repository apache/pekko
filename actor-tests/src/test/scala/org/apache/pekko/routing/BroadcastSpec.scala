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

package org.apache.pekko.routing

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.pattern.ask
import pekko.testkit.{ DefaultTimeout, ImplicitSender, PekkoSpec, TestLatch }

object BroadcastSpec {
  class TestActor extends Actor {
    def receive = { case _ => }
  }
}

class BroadcastSpec extends PekkoSpec with DefaultTimeout with ImplicitSender {

  "broadcast group" must {

    "broadcast message using !" in {
      val doneLatch = new TestLatch(2)

      val counter1 = new AtomicInteger
      val actor1 = system.actorOf(Props(new Actor {
        def receive = {
          case "end"    => doneLatch.countDown()
          case msg: Int => counter1.addAndGet(msg)
        }
      }))

      val counter2 = new AtomicInteger
      val actor2 = system.actorOf(Props(new Actor {
        def receive = {
          case "end"    => doneLatch.countDown()
          case msg: Int => counter2.addAndGet(msg)
        }
      }))

      val paths = List(actor1, actor2).map(_.path.toString)
      val routedActor = system.actorOf(BroadcastGroup(paths).props())
      routedActor ! 1
      routedActor ! "end"

      Await.ready(doneLatch, remainingOrDefault)

      counter1.get should ===(1)
      counter2.get should ===(1)
    }

    "broadcast message using ?" in {
      val doneLatch = new TestLatch(2)

      val counter1 = new AtomicInteger
      val actor1 = system.actorOf(Props(new Actor {
        def receive = {
          case "end"    => doneLatch.countDown()
          case msg: Int =>
            counter1.addAndGet(msg)
            sender() ! "ack"
        }
      }))

      val counter2 = new AtomicInteger
      val actor2 = system.actorOf(Props(new Actor {
        def receive = {
          case "end"    => doneLatch.countDown()
          case msg: Int => counter2.addAndGet(msg)
        }
      }))

      val paths = List(actor1, actor2).map(_.path.toString)
      val routedActor = system.actorOf(BroadcastGroup(paths).props())
      routedActor ? 1
      routedActor ! "end"

      Await.ready(doneLatch, remainingOrDefault)

      counter1.get should ===(1)
      counter2.get should ===(1)
    }
  }

}

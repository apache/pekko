/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch

import scala.concurrent.duration._

import com.typesafe.config.Config
import language.postfixOps

import org.apache.pekko
import pekko.actor.{ Actor, ActorSystem, Props }
import pekko.testkit.{ DefaultTimeout, PekkoSpec }
import pekko.util.unused

object StablePriorityDispatcherSpec {
  case object Result

  val config = """
    unbounded-stable-prio-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.StablePriorityDispatcherSpec$Unbounded"
    }
    bounded-stable-prio-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.StablePriorityDispatcherSpec$Bounded"
    }
    """

  class Unbounded(@unused settings: ActorSystem.Settings, @unused config: Config)
      extends UnboundedStablePriorityMailbox(PriorityGenerator({
        case i: Int if i <= 100 => i // Small integers have high priority
        case _: Int             => 101 // Don't care for other integers
        case Result             => Int.MaxValue
        case _                  => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }: Any => Int))

  class Bounded(@unused settings: ActorSystem.Settings, @unused config: Config)
      extends BoundedStablePriorityMailbox(PriorityGenerator({
          case i: Int if i <= 100 => i // Small integers have high priority
          case _: Int             => 101 // Don't care for other integers
          case Result             => Int.MaxValue
          case _                  => throw new RuntimeException() // compiler exhaustiveness check pleaser
        }: Any => Int), 1000, 10 seconds)

}

class StablePriorityDispatcherSpec extends PekkoSpec(StablePriorityDispatcherSpec.config) with DefaultTimeout {
  import StablePriorityDispatcherSpec._

  "A StablePriorityDispatcher" must {
    "Order its messages according to the specified comparator while preserving FIFO for equal priority messages, " +
    "using an unbounded mailbox" in {
      val dispatcherKey = "unbounded-stable-prio-dispatcher"
      testOrdering(dispatcherKey)
    }

    "Order its messages according to the specified comparator while preserving FIFO for equal priority messages, " +
    "using a bounded mailbox" in {
      val dispatcherKey = "bounded-stable-prio-dispatcher"
      testOrdering(dispatcherKey)
    }

    def testOrdering(dispatcherKey: String): Unit = {
      val msgs = (1 to 200) toList
      val shuffled = scala.util.Random.shuffle(msgs)

      // It's important that the actor under test is not a top level actor
      // with RepointableActorRef, since messages might be queued in
      // UnstartedCell and then sent to the StablePriorityQueue and consumed immediately
      // without the ordering taking place.
      system.actorOf(Props(new Actor {
        context.actorOf(Props(new Actor {

          val acc = scala.collection.mutable.ListBuffer[Int]()

          shuffled.foreach { m =>
            self ! m
          }

          self.tell(Result, testActor)

          def receive = {
            case i: Int => acc += i
            case Result => sender() ! acc.toList
          }
        }).withDispatcher(dispatcherKey))

        def receive = Actor.emptyBehavior

      }))

      // Low messages should come out first, and in priority order.  High messages follow - they are equal priority and
      // should come out in the same order in which they were sent.
      val lo = (1 to 100) toList
      val hi = shuffled.filter { _ > 100 }
      (expectMsgType[List[Int]]: List[Int]) should ===(lo ++ hi)
    }
  }
}

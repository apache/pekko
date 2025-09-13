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

package org.apache.pekko.dispatch

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorSystem, Props }
import pekko.testkit.{ DefaultTimeout, PekkoSpec }
import pekko.util.unused

import com.typesafe.config.Config

object PriorityDispatcherSpec {

  case object Result

  val config = """
    unbounded-prio-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.PriorityDispatcherSpec$Unbounded"
    }
    bounded-prio-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.PriorityDispatcherSpec$Bounded"
    }
    """

  class Unbounded(@unused settings: ActorSystem.Settings, @unused config: Config)
      extends UnboundedPriorityMailbox(PriorityGenerator({
        case i: Int => i // Reverse order
        case Result => Int.MaxValue
        case _      => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }: Any => Int))

  class Bounded(@unused settings: ActorSystem.Settings, @unused config: Config)
      extends BoundedPriorityMailbox(PriorityGenerator({
          case i: Int => i // Reverse order
          case Result => Int.MaxValue
          case _      => throw new RuntimeException() // compiler exhaustiveness check pleaser
        }: Any => Int), 1000, 10.seconds)

}

class PriorityDispatcherSpec extends PekkoSpec(PriorityDispatcherSpec.config) with DefaultTimeout {
  import PriorityDispatcherSpec._

  "A PriorityDispatcher" must {
    "Order it's messages according to the specified comparator using an unbounded mailbox" in {
      val dispatcherKey = "unbounded-prio-dispatcher"
      testOrdering(dispatcherKey)
    }

    "Order it's messages according to the specified comparator using a bounded mailbox" in {
      val dispatcherKey = "bounded-prio-dispatcher"
      testOrdering(dispatcherKey)
    }
  }

  def testOrdering(dispatcherKey: String): Unit = {
    val msgs = (1 to 100).toList

    // It's important that the actor under test is not a top level actor
    // with RepointableActorRef, since messages might be queued in
    // UnstartedCell and the sent to the PriorityQueue and consumed immediately
    // without the ordering taking place.
    system.actorOf(Props(new Actor {
      context.actorOf(Props(new Actor {

        val acc = scala.collection.mutable.ListBuffer[Int]()

        scala.util.Random.shuffle(msgs).foreach { m =>
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

    (expectMsgType[List[Int]]: List[Int]) should ===(msgs)
  }

}

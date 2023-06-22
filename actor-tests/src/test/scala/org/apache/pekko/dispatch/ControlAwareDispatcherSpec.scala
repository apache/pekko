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

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.testkit.{ DefaultTimeout, PekkoSpec }

object ControlAwareDispatcherSpec {
  val config = """
    unbounded-control-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.UnboundedControlAwareMailbox"
    }
    bounded-control-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.BoundedControlAwareMailbox"
    }
    """

  case object ImportantMessage extends ControlMessage
}

class ControlAwareDispatcherSpec extends PekkoSpec(ControlAwareDispatcherSpec.config) with DefaultTimeout {
  import ControlAwareDispatcherSpec.ImportantMessage

  "A ControlAwareDispatcher" must {
    "deliver control messages first using an unbounded mailbox" in {
      val dispatcherKey = "unbounded-control-dispatcher"
      testControl(dispatcherKey)
    }

    "deliver control messages first using a bounded mailbox" in {
      val dispatcherKey = "bounded-control-dispatcher"
      testControl(dispatcherKey)
    }
  }

  def testControl(dispatcherKey: String) = {
    // It's important that the actor under test is not a top level actor
    // with RepointableActorRef, since messages might be queued in
    // UnstartedCell and the sent to the PriorityQueue and consumed immediately
    // without the ordering taking place.
    system.actorOf(Props(new Actor {
      context.actorOf(Props(new Actor {

        self ! "test"
        self ! "test2"
        self ! ImportantMessage

        def receive = {
          case x => testActor ! x
        }
      }).withDispatcher(dispatcherKey))

      def receive = Actor.emptyBehavior

    }))

    expectMsg(ImportantMessage)
    expectMsg("test")
    expectMsg("test2")
  }
}

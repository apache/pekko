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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.Identify
import pekko.actor.Props
import pekko.testkit.PekkoSpec

class RouteeCreationSpec extends PekkoSpec {

  "Creating Routees" must {

    "result in visible routees" in {
      val N = 100
      system.actorOf(RoundRobinPool(N).props(Props(new Actor {
        system.actorSelection(self.path).tell(Identify(self.path), testActor)
        def receive = Actor.emptyBehavior
      })))
      for (i <- 1 to N)
        expectMsgType[ActorIdentity] match {
          case ActorIdentity(_, Some(_)) => // fine
          case x                         => fail(s"routee $i was not found $x")
        }
    }

    "allow sending to context.parent" in {
      val N = 100
      system.actorOf(RoundRobinPool(N).props(Props(new Actor {
        context.parent ! "one"
        def receive = {
          case "one" => testActor.forward("two")
        }
      })))
      val gotit = receiveWhile(messages = N) {
        case "two" => lastSender.toString
      }
      expectNoMessage(100.millis)
      if (gotit.size != N) {
        fail(s"got only ${gotit.size} from [${gotit.mkString(", ")}]")
      }
    }

  }

}

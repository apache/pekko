/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern.extended

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.testkit.PekkoSpec
import pekko.util.Timeout

object ExplicitAskSpec {
  case class Request(respondTo: ActorRef)
  case class Response(sentFrom: ActorRef)
}

class ExplicitAskSpec extends PekkoSpec {
  import ExplicitAskSpec._

  "The “ask” pattern with explicit sender" must {
    "allow to access an explicit reference to actor to respond to" in {
      implicit val timeout: Timeout = Timeout(5.seconds)

      val target = system.actorOf(Props(new Actor {
        def receive = {
          case Request(respondTo) => respondTo ! Response(self)
        }
      }))

      val f = target ? (respondTo => Request(respondTo))
      Await.result(f, timeout.duration) should ===(Response(target))
    }

    "work for ActorSelection" in {
      implicit val timeout: Timeout = Timeout(5.seconds)

      val target = system.actorOf(Props(new Actor {
          def receive = {
            case Request(respondTo) => respondTo ! Response(self)
          }
        }), "select-echo")

      val selection = system.actorSelection("/user/select-echo")
      val f = selection ? (respondTo => Request(respondTo))
      Await.result(f, timeout.duration) should ===(Response(target))
    }
  }

}

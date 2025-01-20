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

package org.apache.pekko.actor

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.pattern.ask
import pekko.testkit.{ DefaultTimeout, EventFilter, ImplicitSender, PekkoSpec }

class SupervisorTreeSpec extends PekkoSpec with ImplicitSender with DefaultTimeout {

  "In a 3 levels deep supervisor tree (linked in the constructor) we" must {

    "be able to kill the middle actor and see itself and its child restarted" in {
      EventFilter[ActorKilledException](occurrences = 1).intercept {
        within(5.seconds) {
          val p = Props(new Actor {
            override val supervisorStrategy =
              OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1.second)(List(classOf[Exception]))
            def receive = {
              case p: Props => sender() ! context.actorOf(p)
            }
            override def preRestart(cause: Throwable, msg: Option[Any]): Unit = { testActor ! self.path }
          })
          val headActor = system.actorOf(p)
          val middleActor = Await.result((headActor ? p).mapTo[ActorRef], timeout.duration)
          val lastActor = Await.result((middleActor ? p).mapTo[ActorRef], timeout.duration)

          middleActor ! Kill
          expectMsg(middleActor.path)
          expectMsg(lastActor.path)
          expectNoMessage(2.seconds)
          system.stop(headActor)
        }
      }
    }
  }
}

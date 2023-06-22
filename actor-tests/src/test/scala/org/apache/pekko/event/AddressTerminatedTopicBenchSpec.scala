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

package org.apache.pekko.event

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Props
import pekko.testkit._

object AddressTerminatedTopicBenchSpec {

  class Subscriber(testActor: ActorRef) extends Actor {
    AddressTerminatedTopic(context.system).subscribe(self)
    testActor ! "started"

    override def postStop(): Unit = {
      AddressTerminatedTopic(context.system).unsubscribe(self)
    }

    def receive = Actor.emptyBehavior
  }
}

class AddressTerminatedTopicBenchSpec extends PekkoSpec("pekko.loglevel=INFO") {
  import AddressTerminatedTopicBenchSpec._

  "Subscribe and unsubscribe of AddressTerminated" must {

    "be quick" in {
      val sys = ActorSystem(system.name + "2", system.settings.config)
      try {
        val num = 20000

        val t1 = System.nanoTime()
        val p = Props(classOf[Subscriber], testActor)
        Vector.fill(num)(sys.actorOf(p))
        receiveN(num, 10.seconds)
        log.info("Starting {} actors took {} ms", num, (System.nanoTime() - t1).nanos.toMillis)

        val t2 = System.nanoTime()
        shutdown(sys, 10.seconds, verifySystemShutdown = true)
        log.info("Stopping {} actors took {} ms", num, (System.nanoTime() - t2).nanos.toMillis)
      } finally {
        shutdown(sys)
      }
    }

  }
}

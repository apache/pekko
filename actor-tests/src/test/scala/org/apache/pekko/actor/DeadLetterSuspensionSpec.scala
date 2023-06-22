/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.testkit.EventFilter
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors

object DeadLetterSuspensionSpec {

  object Dropping {
    def props(): Props = Props(new Dropping)
  }

  class Dropping extends Actor {
    override def receive: Receive = {
      case n: Int =>
        context.system.eventStream.publish(Dropped(n, "Don't like numbers", self))
    }
  }

  object Unandled {
    def props(): Props = Props(new Unandled)
  }

  class Unandled extends Actor {
    override def receive: Receive = {
      case n: Int => unhandled(n)
    }
  }
}

class DeadLetterSuspensionSpec extends PekkoSpec("""
  pekko.loglevel = INFO
  pekko.log-dead-letters = 4
  pekko.log-dead-letters-suspend-duration = 2s
  """) with ImplicitSender {
  import DeadLetterSuspensionSpec._

  private val deadActor = system.actorOf(TestActors.echoActorProps)
  watch(deadActor)
  deadActor ! PoisonPill
  expectTerminated(deadActor)

  private val droppingActor = system.actorOf(Dropping.props(), "droppingActor")
  private val unhandledActor = system.actorOf(Unandled.props(), "unhandledActor")

  private def expectedDeadLettersLogMessage(count: Int): String =
    s"Message [java.lang.Integer] from $testActor to $deadActor was not delivered. [$count] dead letters encountered"

  private def expectedDroppedLogMessage(count: Int): String =
    s"Message [java.lang.Integer] to $droppingActor was dropped. Don't like numbers. [$count] dead letters encountered"

  private def expectedUnhandledLogMessage(count: Int): String =
    s"Message [java.lang.Integer] from $testActor to $unhandledActor was unhandled. [$count] dead letters encountered"

  "must suspend dead-letters logging when reaching 'pekko.log-dead-letters', and then re-enable" in {
    EventFilter.info(start = expectedDeadLettersLogMessage(1), occurrences = 1).intercept {
      deadActor ! 1
    }
    EventFilter.info(start = expectedDroppedLogMessage(2), occurrences = 1).intercept {
      droppingActor ! 2
    }
    EventFilter.info(start = expectedUnhandledLogMessage(3), occurrences = 1).intercept {
      unhandledActor ! 3
    }
    EventFilter
      .info(start = expectedDeadLettersLogMessage(4) + ", no more dead letters will be logged in next", occurrences = 1)
      .intercept {
        deadActor ! 4
      }
    deadActor ! 5
    droppingActor ! 6

    // let suspend-duration elapse
    Thread.sleep(2050)

    // re-enabled
    EventFilter
      .info(start = expectedDeadLettersLogMessage(7) + ", of which 2 were not logged", occurrences = 1)
      .intercept {
        deadActor ! 7
      }

    // reset count
    EventFilter.info(start = expectedDeadLettersLogMessage(1), occurrences = 1).intercept {
      deadActor ! 8
    }

  }

}

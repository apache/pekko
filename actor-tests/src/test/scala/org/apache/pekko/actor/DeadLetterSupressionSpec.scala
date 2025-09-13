/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import scala.concurrent.duration._

import org.apache.pekko
import pekko.event.Logging
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

object DeadLetterSupressionSpec {

  case object NormalMsg

  case object SuppressedMsg extends DeadLetterSuppression

}

class DeadLetterSupressionSpec extends PekkoSpec with ImplicitSender {
  import DeadLetterSupressionSpec._

  val deadActor = system.actorOf(TestActors.echoActorProps)
  watch(deadActor)
  deadActor ! PoisonPill
  expectTerminated(deadActor)

  s"must suppress message from default dead-letters logging (sent to dead: ${Logging.simpleName(deadActor)})" in {
    val deadListener = TestProbe()
    system.eventStream.subscribe(deadListener.ref, classOf[DeadLetter])

    val suppressedListener = TestProbe()
    system.eventStream.subscribe(suppressedListener.ref, classOf[SuppressedDeadLetter])

    val allListener = TestProbe()
    system.eventStream.subscribe(allListener.ref, classOf[AllDeadLetters])

    deadActor ! SuppressedMsg
    deadActor ! NormalMsg

    deadListener.expectMsg(DeadLetter(NormalMsg, testActor, deadActor))
    deadListener.expectNoMessage()

    suppressedListener.expectMsg(SuppressedDeadLetter(SuppressedMsg, testActor, system.deadLetters))
    suppressedListener.expectNoMessage()

    allListener.expectMsg(SuppressedDeadLetter(SuppressedMsg, testActor, system.deadLetters))
    allListener.expectMsg(DeadLetter(NormalMsg, testActor, deadActor))
    allListener.expectNoMessage()

    // unwrap for ActorSelection
    system.actorSelection(deadActor.path) ! SuppressedMsg
    system.actorSelection(deadActor.path) ! NormalMsg

    // the recipient ref isn't the same as deadActor here so only checking the message
    deadListener.expectMsgType[DeadLetter].message should ===(NormalMsg)
    suppressedListener.expectMsgType[SuppressedDeadLetter].message should ===(SuppressedMsg)
    deadListener.expectNoMessage()
  }

  s"must suppress message from default dead-letters logging (sent to dead: ${Logging.simpleName(system.deadLetters)})" in {
    val deadListener = TestProbe()
    system.eventStream.subscribe(deadListener.ref, classOf[DeadLetter])

    val suppressedListener = TestProbe()
    system.eventStream.subscribe(suppressedListener.ref, classOf[SuppressedDeadLetter])

    val allListener = TestProbe()
    system.eventStream.subscribe(allListener.ref, classOf[AllDeadLetters])

    system.deadLetters ! SuppressedMsg
    system.deadLetters ! NormalMsg

    deadListener.expectMsg(200.millis, DeadLetter(NormalMsg, testActor, system.deadLetters))

    suppressedListener.expectMsg(200.millis, SuppressedDeadLetter(SuppressedMsg, testActor, system.deadLetters))

    allListener.expectMsg(200.millis, SuppressedDeadLetter(SuppressedMsg, testActor, system.deadLetters))
    allListener.expectMsg(200.millis, DeadLetter(NormalMsg, testActor, system.deadLetters))

    Thread.sleep(200)
    deadListener.expectNoMessage(Duration.Zero)
    suppressedListener.expectNoMessage(Duration.Zero)
    allListener.expectNoMessage(Duration.Zero)

    // unwrap for ActorSelection
    system.actorSelection(system.deadLetters.path) ! SuppressedMsg
    system.actorSelection(system.deadLetters.path) ! NormalMsg

    deadListener.expectMsg(DeadLetter(NormalMsg, testActor, system.deadLetters))
    suppressedListener.expectMsg(SuppressedDeadLetter(SuppressedMsg, testActor, system.deadLetters))
    deadListener.expectNoMessage()
  }
}

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

package org.apache.pekko.pattern

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }

object PromiseRefSpec {
  case class Request(replyTo: ActorRef)
  case object Response

  case object FirstMessage
  case object SecondMessage
}

class PromiseRefSpec extends PekkoSpec with ImplicitSender {
  import PromiseRefSpec._

  import pekko.pattern._

  "The PromiseRef" must {
    "complete promise with received message" in {
      val promiseRef = PromiseRef(5.seconds)

      val target = system.actorOf(Props(new Actor {
        def receive = { case Request(replyTo) => replyTo ! Response }
      }))

      target ! Request(promiseRef.ref)
      Await.result(promiseRef.future, 5.seconds) should ===(Response)
    }

    "throw IllegalArgumentException on negative timeout" in {
      intercept[IllegalArgumentException] {
        PromiseRef(-5.seconds)
      }
    }

    "receive only one message" in {
      val deadListener = TestProbe()
      system.eventStream.subscribe(deadListener.ref, classOf[DeadLetter])

      val promiseRef = PromiseRef(5.seconds)

      promiseRef.ref ! FirstMessage
      Await.result(promiseRef.future, 5.seconds) should ===(FirstMessage)

      promiseRef.ref ! SecondMessage
      deadListener.expectMsgType[DeadLetter].message should ===(SecondMessage)
    }

    "work with explicitly constructed PromiseRef's" in {
      val promise = Promise[Int]()

      val alice = system.actorOf(Props(new Actor {
        def receive = { case Response => promise.success(42) }
      }))

      val promiseRef = PromiseRef.wrap(alice, promise)

      val bob = system.actorOf(Props(new Actor {
        def receive = { case Request(replyTo) => replyTo ! Response }
      }))

      bob ! Request(promiseRef.ref)
      promiseRef.future.futureValue should ===(42)
    }
  }
}

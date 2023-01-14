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

package org.apache.pekko.dataflow

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.ExecutionException
import scala.concurrent.Future
import scala.concurrent.duration._

import language.postfixOps

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.actor.ActorRef
import pekko.pattern.{ ask, pipe }
import pekko.testkit.{ DefaultTimeout, PekkoSpec }

class Future2ActorSpec extends PekkoSpec with DefaultTimeout {
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  "The Future2Actor bridge" must {

    "support convenient sending to multiple destinations" in {
      Future(42).pipeTo(testActor).pipeTo(testActor)
      expectMsgAllOf(1 second, 42, 42)
    }

    "support convenient sending to multiple destinations with implicit sender" in {
      implicit val someActor: ActorRef = system.actorOf(Props(new Actor { def receive = Actor.emptyBehavior }))
      Future(42).pipeTo(testActor).pipeTo(testActor)
      expectMsgAllOf(1 second, 42, 42)
      lastSender should ===(someActor)
    }

    "support convenient sending with explicit sender" in {
      val someActor = system.actorOf(Props(new Actor { def receive = Actor.emptyBehavior }))
      Future(42).to(testActor, someActor)
      expectMsgAllOf(1 second, 42)
      lastSender should ===(someActor)
    }

    "support reply via sender" in {
      val actor = system.actorOf(Props(new Actor {
        def receive = {
          case "do" => Future(31).pipeTo(context.sender())
          case "ex" => Future(throw new AssertionError).pipeTo(context.sender())
        }
      }))
      Await.result(actor ? "do", timeout.duration) should ===(31)
      intercept[ExecutionException] {
        Await.result(actor ? "ex", timeout.duration)
      }.getCause.isInstanceOf[AssertionError] should ===(true)
    }
  }
}

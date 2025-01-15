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

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

import org.apache.pekko
import pekko.pattern.{ ask, pipe }
import pekko.testkit._

object ForwardActorSpec {
  val ExpectedMessage = "FOO"

  def createForwardingChain(system: ActorSystem): ActorRef = {
    val replier = system.actorOf(Props(new Actor {
      def receive = { case x => sender() ! x }
    }))

    def mkforwarder(forwardTo: ActorRef) =
      system.actorOf(Props(new Actor {
        def receive = { case x => forwardTo.forward(x) }
      }))

    mkforwarder(mkforwarder(mkforwarder(replier)))
  }
}

class ForwardActorSpec extends PekkoSpec {
  import ForwardActorSpec._
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  "A Forward Actor" must {

    "forward actor reference when invoking forward on tell" in {
      val replyTo = system.actorOf(Props(new Actor {
        def receive = { case ExpectedMessage => testActor ! ExpectedMessage }
      }))

      val chain = createForwardingChain(system)

      chain.tell(ExpectedMessage, replyTo)
      expectMsg(5.seconds, ExpectedMessage)
    }

    "forward actor reference when invoking forward on ask" in {
      val chain = createForwardingChain(system)
      chain.ask(ExpectedMessage)(5.seconds).pipeTo(testActor)
      expectMsg(5.seconds, ExpectedMessage)
    }
  }
}

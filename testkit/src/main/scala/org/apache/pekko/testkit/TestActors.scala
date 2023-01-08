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

package org.apache.pekko.testkit

import org.apache.pekko.actor.{ Actor, ActorRef, Props }

/**
 * A collection of common actor patterns used in tests.
 */
object TestActors {

  /**
   * EchoActor sends back received messages (unmodified).
   */
  class EchoActor extends Actor {
    override def receive = {
      case message => sender() ! message
    }
  }

  /**
   * BlackholeActor does nothing for incoming messages, its like a blackhole.
   */
  class BlackholeActor extends Actor {
    override def receive = {
      case _ => // ignore...
    }
  }

  /**
   * ForwardActor forwards all messages as-is to specified ActorRef.
   *
   * @param ref target ActorRef to forward messages to
   */
  class ForwardActor(ref: ActorRef) extends Actor {
    override def receive = {
      case message => ref.forward(message)
    }
  }

  val echoActorProps = Props[EchoActor]()
  val blackholeProps = Props[BlackholeActor]()
  def forwardActorProps(ref: ActorRef) = Props(classOf[ForwardActor], ref)

}

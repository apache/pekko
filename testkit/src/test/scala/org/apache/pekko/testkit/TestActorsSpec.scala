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

package org.apache.pekko.testkit

class TestActorsSpec extends PekkoSpec with ImplicitSender {

  import TestActors.{ echoActorProps, forwardActorProps }

  "A EchoActor" must {
    "send back messages unchanged" in {
      val message = "hello world"
      val echo = system.actorOf(echoActorProps)

      echo ! message

      expectMsg(message)
    }
  }

  "A ForwardActor" must {
    "forward messages to target actor" in {
      val message = "forward me"
      val forward = system.actorOf(forwardActorProps(testActor))

      forward ! message

      expectMsg(message)
    }
  }
}

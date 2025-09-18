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

package org.apache.pekko.actor.typed

import org.apache.pekko
import pekko.actor.ActorPath
import pekko.actor.ActorRefProvider
import pekko.actor.ActorSystemImpl
import pekko.actor.MinimalActorRef
import pekko.actor.RootActorPath
import pekko.actor.typed.scaladsl.Behaviors

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActorRefResolverSpec extends AnyWordSpec with ScalaFutures with Matchers {
  "ActorRefResolver" should {
    "not allow serialization of ref originating from other system" in {
      val system1 = ActorSystem(Behaviors.empty[String], "sys1")
      val system2 = ActorSystem(Behaviors.empty[String], "sys2")
      try {
        val ref1 = system1.systemActorOf(Behaviors.empty, "ref1")
        val serialized = ActorRefResolver(system1).toSerializationFormat(ref1)
        serialized should startWith("pekko://sys1/")

        intercept[IllegalArgumentException] {
          // wrong system
          ActorRefResolver(system2).toSerializationFormat(ref1)
        }

        // we can't detect that for MinimalActorRef
        import pekko.actor.typed.scaladsl.adapter._

        val minRef1: pekko.actor.ActorRef = new MinimalActorRef {
          override def provider: ActorRefProvider = system1.toClassic.asInstanceOf[ActorSystemImpl].provider
          override def path: ActorPath = RootActorPath(system1.address) / "minRef1"
        }

        val minRef1Serialized = ActorRefResolver(system2).toSerializationFormat(minRef1)
        minRef1Serialized should startWith("pekko://sys2/")

      } finally {
        system1.terminate()
        system2.terminate()
      }
    }

  }

}

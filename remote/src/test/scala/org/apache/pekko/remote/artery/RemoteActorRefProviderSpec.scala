/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.{ EmptyLocalActorRef, InternalActorRef }
import pekko.actor.ActorRefScope
import pekko.actor.ExtendedActorSystem
import pekko.remote.RemoteActorRef
import pekko.testkit.{ EventFilter, TestActors }

class RemoteActorRefProviderSpec extends ArteryMultiNodeSpec {

  val addressA = address(localSystem)
  system.actorOf(TestActors.echoActorProps, "echo")

  val systemB = newRemoteSystem()
  val addressB = address(systemB)
  systemB.actorOf(TestActors.echoActorProps, "echo")

  "RemoteActorRefProvider" must {

    "resolve local actor selection" in {
      val sel = system.actorSelection(s"pekko://${system.name}@${addressA.host.get}:${addressA.port.get}/user/echo")
      sel.anchor.asInstanceOf[InternalActorRef].isLocal should be(true)
    }

    "resolve remote actor selection" in {
      val sel = system.actorSelection(s"pekko://${systemB.name}@${addressB.host.get}:${addressB.port.get}/user/echo")
      sel.anchor.getClass should ===(classOf[RemoteActorRef])
      sel.anchor.asInstanceOf[InternalActorRef].isLocal should be(false)
    }

    "cache resolveActorRef for local ref" in {
      val provider = localSystem.asInstanceOf[ExtendedActorSystem].provider
      val path = s"pekko://${system.name}@${addressA.host.get}:${addressA.port.get}/user/echo"
      val ref1 = provider.resolveActorRef(path)
      ref1.getClass should !==(classOf[EmptyLocalActorRef])
      ref1.asInstanceOf[ActorRefScope].isLocal should ===(true)

      val ref2 = provider.resolveActorRef(path)
      (ref1 should be).theSameInstanceAs(ref2)
    }

    "not cache resolveActorRef for unresolved ref" in {
      val provider = localSystem.asInstanceOf[ExtendedActorSystem].provider
      val path = s"pekko://${system.name}@${addressA.host.get}:${addressA.port.get}/user/doesNotExist"
      val ref1 = provider.resolveActorRef(path)
      ref1.getClass should ===(classOf[EmptyLocalActorRef])

      val ref2 = provider.resolveActorRef(path)
      ref1 should not be theSameInstanceAs(ref2)
    }

    "cache resolveActorRef for remote ref" in {
      val provider = localSystem.asInstanceOf[ExtendedActorSystem].provider
      val path = s"pekko://${systemB.name}@${addressB.host.get}:${addressB.port.get}/user/echo"
      val ref1 = provider.resolveActorRef(path)
      ref1.getClass should ===(classOf[RemoteActorRef])

      val ref2 = provider.resolveActorRef(path)
      (ref1 should be).theSameInstanceAs(ref2)
    }

    "detect wrong protocol" in {
      EventFilter[IllegalArgumentException](start = "No root guardian at", occurrences = 1).intercept {
        val sel =
          system.actorSelection(s"pekko.tcp://${systemB.name}@${addressB.host.get}:${addressB.port.get}/user/echo")
        sel.anchor.getClass should ===(classOf[EmptyLocalActorRef])
      }
    }

  }

}

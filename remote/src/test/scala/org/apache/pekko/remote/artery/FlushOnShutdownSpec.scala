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

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ Actor, ActorIdentity, Identify, Props }
import pekko.testkit.TestProbe

class FlushOnShutdownSpec extends ArteryMultiNodeSpec(ArterySpecSupport.defaultConfig) {

  val remoteSystem = newRemoteSystem()

  "Artery" must {

    "flush messages enqueued before shutdown" in {

      val probe = TestProbe()
      val probeRef = probe.ref

      localSystem.actorOf(Props(new Actor {
          def receive = {
            case msg => probeRef ! msg
          }
        }), "receiver")

      val actorOnSystemB = remoteSystem.actorOf(Props(new Actor {
          def receive = {
            case "start" =>
              context.actorSelection(rootActorPath(localSystem) / "user" / "receiver") ! Identify(None)

            case ActorIdentity(_, Some(receiverRef)) =>
              receiverRef ! "msg1"
              receiverRef ! "msg2"
              receiverRef ! "msg3"
              context.system.terminate()
          }
        }), "sender")

      actorOnSystemB ! "start"

      probe.expectMsg("msg1")
      probe.expectMsg("msg2")
      probe.expectMsg("msg3")

      Await.result(remoteSystem.whenTerminated, 6.seconds)
    }

  }

}

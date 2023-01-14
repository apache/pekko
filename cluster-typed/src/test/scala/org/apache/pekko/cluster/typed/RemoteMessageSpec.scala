/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed

import scala.concurrent.Promise

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.Done
import pekko.actor.{ ActorSystem => ClassicActorSystem }
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorRefResolver
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.serialization.jackson.CborSerializable
import pekko.testkit.PekkoSpec

object RemoteMessageSpec {
  def config = ConfigFactory.parseString(s"""
    pekko {
      loglevel = debug
      actor.provider = cluster
      remote.classic.netty.tcp.port = 0
      remote.artery {
        canonical {
          hostname = 127.0.0.1
          port = 0
        }
      }
    }
    """)

  case class Ping(sender: ActorRef[String]) extends CborSerializable
}

class RemoteMessageSpec extends PekkoSpec(RemoteMessageSpec.config) {

  import RemoteMessageSpec._

  val typedSystem = system.toTyped

  "the adapted system" should {

    "something something" in {

      val pingPromise = Promise[Done]()
      val ponger = Behaviors.receive[Ping]((_, msg) =>
        msg match {
          case Ping(sender) =>
            pingPromise.success(Done)
            sender ! "pong"
            Behaviors.stopped
        })

      // typed actor on system1
      val pingPongActor = system.spawn(ponger, "pingpong")

      val system2 = ClassicActorSystem(system.name + "-system2", RemoteMessageSpec.config)
      val typedSystem2 = system2.toTyped
      try {

        // resolve the actor from node2
        val remoteRefStr = ActorRefResolver(typedSystem).toSerializationFormat(pingPongActor)
        val remoteRef: ActorRef[Ping] =
          ActorRefResolver(typedSystem2).resolveActorRef[Ping](remoteRefStr)

        val pongPromise = Promise[Done]()
        val recipient = system2.spawn(Behaviors.receive[String] { (_, _) =>
            pongPromise.success(Done)
            Behaviors.stopped
          }, "recipient")
        remoteRef ! Ping(recipient)

        pingPromise.future.futureValue should ===(Done)
        pongPromise.future.futureValue should ===(Done)

      } finally {
        system2.terminate()
      }
    }

  }

}

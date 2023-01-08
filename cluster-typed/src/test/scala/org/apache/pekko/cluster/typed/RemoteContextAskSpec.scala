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

package org.apache.pekko.cluster.typed

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.receptionist.Receptionist
import pekko.actor.typed.receptionist.Receptionist.Registered
import pekko.actor.typed.receptionist.ServiceKey
import pekko.actor.typed.scaladsl.Behaviors
import pekko.serialization.jackson.CborSerializable
import pekko.util.Timeout

object RemoteContextAskSpec {
  def config = ConfigFactory.parseString(s"""
    pekko {
      loglevel = debug
      actor.provider = cluster
      remote.classic.netty.tcp.port = 0
      remote.classic.netty.tcp.host = 127.0.0.1
      remote.artery {
        canonical {
          hostname = 127.0.0.1
          port = 0
        }
      }
    }
  """)

  case object Pong extends CborSerializable
  case class Ping(respondTo: ActorRef[Pong.type]) extends CborSerializable

  def pingPong = Behaviors.receive[Ping] { (_, msg) =>
    msg match {
      case Ping(sender) =>
        sender ! Pong
        Behaviors.same
    }
  }

  val pingPongKey = ServiceKey[Ping]("ping-pong")

}

class RemoteContextAskSpec
    extends ScalaTestWithActorTestKit(RemoteContextAskSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  import RemoteContextAskSpec._

  "Asking another actor through the ActorContext across remoting" must {

    "work" in {
      val node1 = Cluster(system)
      val node1Probe = TestProbe[AnyRef]()(system)
      node1.manager ! Join(node1.selfMember.address)

      Receptionist(system).ref ! Receptionist.Subscribe(pingPongKey, node1Probe.ref)
      node1Probe.expectMessageType[Receptionist.Listing]

      val system2 = ActorSystem(pingPong, system.name, system.settings.config)
      val node2 = Cluster(system2)
      node2.manager ! Join(node1.selfMember.address)

      val node2Probe = TestProbe[AnyRef]()(system2)
      Receptionist(system2).ref ! Receptionist.Register(pingPongKey, system2, node2Probe.ref)
      node2Probe.expectMessageType[Registered]

      // wait until the service is seen on the first node
      val remoteRef = node1Probe.expectMessageType[Receptionist.Listing].serviceInstances(pingPongKey).head

      spawn(Behaviors.setup[AnyRef] { ctx =>
        implicit val timeout: Timeout = 3.seconds

        ctx.ask(remoteRef, Ping.apply) {
          case Success(pong) => pong
          case Failure(ex)   => ex
        }

        Behaviors.receiveMessage { msg =>
          node1Probe.ref ! msg
          Behaviors.same
        }
      })

      node1Probe.expectMessageType[Pong.type]

    }

  }

}

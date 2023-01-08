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

import java.util.UUID

import scala.concurrent.duration._

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorPath
import pekko.actor.ActorRef
import pekko.actor.ActorSelection
import pekko.actor.Deploy
import pekko.actor.Identify
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.testkit.{ ImplicitSender, TestActors, TestProbe }

class ArteryUpdSendConsistencyWithOneLaneSpec
    extends AbstractRemoteSendConsistencySpec(ConfigFactory.parseString("""
      pekko.remote.artery.transport = aeron-udp
      pekko.remote.artery.advanced.outbound-lanes = 1
      pekko.remote.artery.advanced.inbound-lanes = 1
    """).withFallback(ArterySpecSupport.defaultConfig))

class ArteryUpdSendConsistencyWithThreeLanesSpec
    extends AbstractRemoteSendConsistencySpec(ConfigFactory.parseString("""
      pekko.loglevel = DEBUG
      pekko.remote.artery.transport = aeron-udp
      pekko.remote.artery.advanced.outbound-lanes = 3
      pekko.remote.artery.advanced.inbound-lanes = 3
    """).withFallback(ArterySpecSupport.defaultConfig))

class ArteryTcpSendConsistencyWithOneLaneSpec
    extends AbstractRemoteSendConsistencySpec(ConfigFactory.parseString("""
      pekko.remote.artery.transport = tcp
      pekko.remote.artery.advanced.outbound-lanes = 1
      pekko.remote.artery.advanced.inbound-lanes = 1
    """).withFallback(ArterySpecSupport.defaultConfig))

class ArteryTcpSendConsistencyWithThreeLanesSpec
    extends AbstractRemoteSendConsistencySpec(ConfigFactory.parseString("""
      pekko.remote.artery.transport = tcp
      pekko.remote.artery.advanced.outbound-lanes = 3
      pekko.remote.artery.advanced.inbound-lanes = 3
    """).withFallback(ArterySpecSupport.defaultConfig))

class ArteryTlsTcpSendConsistencyWithOneLaneSpec
    extends AbstractRemoteSendConsistencySpec(ConfigFactory.parseString("""
      pekko.remote.artery.transport = tls-tcp
      pekko.remote.artery.advanced.outbound-lanes = 1
      pekko.remote.artery.advanced.inbound-lanes = 1
    """).withFallback(ArterySpecSupport.defaultConfig))

class ArteryTlsTcpSendConsistencyWithThreeLanesSpec
    extends AbstractRemoteSendConsistencySpec(ConfigFactory.parseString("""
      pekko.remote.artery.transport = tls-tcp
      pekko.remote.artery.advanced.outbound-lanes = 1
      pekko.remote.artery.advanced.inbound-lanes = 1
    """).withFallback(ArterySpecSupport.defaultConfig))

abstract class AbstractRemoteSendConsistencySpec(config: Config)
    extends ArteryMultiNodeSpec(config)
    with ImplicitSender {

  val systemB = newRemoteSystem(name = Some("systemB"))
  val addressB = address(systemB)
  val rootB = RootActorPath(addressB)

  private def actorRefBySelection(path: ActorPath) = {

    val correlationId = Some(UUID.randomUUID().toString)
    system.actorSelection(path) ! Identify(correlationId)

    val actorIdentity = expectMsgType[ActorIdentity](5.seconds)
    actorIdentity.correlationId shouldBe correlationId

    actorIdentity.ref.get
  }

  "Artery" must {

    "be able to identify a remote actor and ping it" in {
      systemB.actorOf(Props(new Actor {
          def receive = {
            case "ping" => sender() ! "pong"
          }
        }), "echo")

      val actorPath = rootB / "user" / "echo"
      val echoSel = system.actorSelection(actorPath)
      val echoRef = actorRefBySelection(actorPath)

      echoRef ! "ping"
      expectMsg("pong")

      echoRef ! "ping"
      expectMsg("pong")

      echoRef ! "ping"
      expectMsg("pong")

      // and actorSelection
      echoSel ! "ping"
      expectMsg("pong")

      echoSel ! "ping"
      expectMsg("pong")
    }

    "not send to remote re-created actor with same name" in {
      val echo = systemB.actorOf(TestActors.echoActorProps, "otherEcho1")
      echo ! 71
      expectMsg(71)
      echo ! PoisonPill
      echo ! 72
      val probe = TestProbe()(systemB)
      probe.watch(echo)
      probe.expectTerminated(echo)
      expectNoMessage(1.second)

      val echo2 = systemB.actorOf(TestActors.echoActorProps, "otherEcho1")
      echo2 ! 73
      expectMsg(73)
      // msg to old ActorRef (different uid) should not get through
      echo2.path.uid should not be (echo.path.uid)
      echo ! 74
      expectNoMessage(1.second)
    }

    "be able to send messages concurrently preserving order" in {
      systemB.actorOf(TestActors.echoActorProps, "echoA")
      systemB.actorOf(TestActors.echoActorProps, "echoB")
      systemB.actorOf(TestActors.echoActorProps, "echoC")

      val remoteRefA = actorRefBySelection(rootB / "user" / "echoA")
      val remoteRefB = actorRefBySelection(rootB / "user" / "echoB")
      val remoteRefC = actorRefBySelection(rootB / "user" / "echoC")

      def senderProps(remoteRef: ActorRef) =
        Props(new Actor {
          var counter = 1000
          remoteRef ! counter

          override def receive: Receive = {
            case i: Int =>
              if (i != counter) testActor ! s"Failed, expected $counter got $i"
              else if (counter == 0) {
                testActor ! "success"
                context.stop(self)
              } else {
                counter -= 1
                remoteRef ! counter
              }
          }
        }).withDeploy(Deploy.local)

      system.actorOf(senderProps(remoteRefA))
      system.actorOf(senderProps(remoteRefB))
      system.actorOf(senderProps(remoteRefC))
      system.actorOf(senderProps(remoteRefA))

      within(10.seconds) {
        expectMsg("success")
        expectMsg("success")
        expectMsg("success")
        expectMsg("success")
      }
    }

    "be able to send messages with actorSelection concurrently preserving order" in {
      systemB.actorOf(TestActors.echoActorProps, "echoA2")
      systemB.actorOf(TestActors.echoActorProps, "echoB2")
      systemB.actorOf(TestActors.echoActorProps, "echoC2")

      val selA = system.actorSelection(rootB / "user" / "echoA2")
      val selB = system.actorSelection(rootB / "user" / "echoB2")
      val selC = system.actorSelection(rootB / "user" / "echoC2")

      def senderProps(sel: ActorSelection) =
        Props(new Actor {
          var counter = 1000
          sel ! counter

          override def receive: Receive = {
            case i: Int =>
              if (i != counter) testActor ! s"Failed, expected $counter got $i"
              else if (counter == 0) {
                testActor ! "success2"
                context.stop(self)
              } else {
                counter -= 1
                sel ! counter
              }
          }
        }).withDeploy(Deploy.local)

      system.actorOf(senderProps(selA))
      system.actorOf(senderProps(selB))
      system.actorOf(senderProps(selC))
      system.actorOf(senderProps(selA))

      within(10.seconds) {
        expectMsg("success2")
        expectMsg("success2")
        expectMsg("success2")
        expectMsg("success2")
      }
    }

  }

}

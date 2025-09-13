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

package org.apache.pekko.remote.classic

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ ActorIdentity, Identify, _ }
import pekko.remote.{ RARP, RemotingMultiNodeSpec }
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.AssociationHandle
import pekko.remote.transport.ThrottlerTransportAdapter.ForceDisassociateExplicitly
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object RemoteNodeRestartGateSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      pekko.remote.artery.enabled = off
      pekko.loglevel = INFO
      pekko.remote.classic.log-remote-lifecycle-events = INFO
      pekko.remote.classic.retry-gate-closed-for  = 1d # Keep it long
                              """)))

  testTransport(on = true)

  class Subject extends Actor {
    def receive = {
      case "shutdown" => context.system.terminate()
      case msg        => sender() ! msg
    }
  }

}

class RemoteNodeRestartGateSpecMultiJvmNode1 extends RemoteNodeRestartGateSpec
class RemoteNodeRestartGateSpecMultiJvmNode2 extends RemoteNodeRestartGateSpec

@nowarn("msg=deprecated")
abstract class RemoteNodeRestartGateSpec extends RemotingMultiNodeSpec(RemoteNodeRestartGateSpec) {

  import RemoteNodeRestartGateSpec._

  override def initialParticipants = 2

  def identify(role: RoleName, actorName: String): ActorRef = {
    system.actorSelection(node(role) / "user" / actorName) ! Identify(actorName)
    expectMsgType[ActorIdentity].ref.get
  }

  "RemoteNodeRestartGate" must {

    "allow restarted node to pass through gate" taggedAs LongRunningTest in {

      system.actorOf(Props[Subject](), "subject")
      enterBarrier("subject-started")

      runOn(first) {
        val secondAddress = node(second).address

        identify(second, "subject")

        EventFilter.warning(pattern = "address is now gated", occurrences = 1).intercept {
          Await.result(
            RARP(system).provider.transport
              .managementCommand(ForceDisassociateExplicitly(node(second).address, AssociationHandle.Unknown)),
            3.seconds)
        }

        enterBarrier("gated")

        testConductor.shutdown(second).await

        within(10.seconds) {
          awaitAssert {
            system.actorSelection(RootActorPath(secondAddress) / "user" / "subject") ! Identify("subject")
            expectMsgType[ActorIdentity].ref.get
          }
        }

        system.actorSelection(RootActorPath(secondAddress) / "user" / "subject") ! "shutdown"
      }

      runOn(second) {
        val address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        val firstAddress = node(first).address

        enterBarrier("gated")

        Await.ready(system.whenTerminated, 10.seconds)

        val freshSystem = ActorSystem(
          system.name,
          ConfigFactory.parseString(s"""
                    pekko.remote.retry-gate-closed-for = 0.5 s
                    pekko.remote.classic.netty.tcp {
                      hostname = ${address.host.get}
                      port = ${address.port.get}
                    }
                    """).withFallback(system.settings.config))

        val probe = TestProbe()(freshSystem)

        // Pierce the gate
        within(30.seconds) {
          awaitAssert {
            freshSystem
              .actorSelection(RootActorPath(firstAddress) / "user" / "subject")
              .tell(Identify("subject"), probe.ref)
            probe.expectMsgType[ActorIdentity].ref.get
          }
        }

        // Now the other system will be able to pass, too
        freshSystem.actorOf(Props[Subject](), "subject")

        Await.ready(freshSystem.whenTerminated, 30.seconds)
      }

    }

  }
}

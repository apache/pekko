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

package org.apache.pekko.remote.artery

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ ActorIdentity, Identify, _ }
import pekko.remote.{ AddressUidExtension, RARP, RemotingMultiNodeSpec }
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

object RemoteRestartedQuarantinedSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      pekko.loglevel = WARNING
      pekko.remote.log-remote-lifecycle-events = WARNING
      pekko.remote.artery.enabled = on
      # test is using Java serialization and not priority to rewrite
      pekko.actor.allow-java-serialization = on
      pekko.actor.warn-about-java-serializer-usage = off
      """))
      .withFallback(RemotingMultiNodeSpec.commonConfig))

  class Subject extends Actor {
    def receive = {
      case "shutdown" => context.system.terminate()
      case "identify" => sender() ! (AddressUidExtension(context.system).longAddressUid -> self)
    }
  }

}

class RemoteRestartedQuarantinedSpecMultiJvmNode1 extends RemoteRestartedQuarantinedSpec
class RemoteRestartedQuarantinedSpecMultiJvmNode2 extends RemoteRestartedQuarantinedSpec

abstract class RemoteRestartedQuarantinedSpec extends RemotingMultiNodeSpec(RemoteRestartedQuarantinedSpec) {

  import RemoteRestartedQuarantinedSpec._

  override def initialParticipants = 2

  def identifyWithUid(
      role: RoleName,
      actorName: String,
      timeout: FiniteDuration = remainingOrDefault): (Long, ActorRef) = {
    within(timeout) {
      system.actorSelection(node(role) / "user" / actorName) ! "identify"
      expectMsgType[(Long, ActorRef)]
    }
  }

  "A restarted quarantined system" must {

    "should not crash the other system (#17213)" taggedAs LongRunningTest in {

      system.actorOf(Props[Subject](), "subject")
      enterBarrier("subject-started")

      runOn(first) {
        val secondAddress = node(second).address

        val (uid, _) = identifyWithUid(second, "subject", 5.seconds)

        enterBarrier("before-quarantined")
        RARP(system).provider.transport.quarantine(node(second).address, Some(uid), "test")

        enterBarrier("quarantined")
        enterBarrier("still-quarantined")

        testConductor.shutdown(second).await

        within(30.seconds) {
          awaitAssert {
            system.actorSelection(RootActorPath(secondAddress) / "user" / "subject") ! Identify("subject")
            expectMsgType[ActorIdentity](1.second).ref.get
          }
        }

        system.actorSelection(RootActorPath(secondAddress) / "user" / "subject") ! "shutdown"
      }

      runOn(second) {
        val address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        val firstAddress = node(first).address
        system.eventStream.subscribe(testActor, classOf[ThisActorSystemQuarantinedEvent])

        val (firstUid, _) = identifyWithUid(first, "subject", 5.seconds)

        enterBarrier("before-quarantined")
        enterBarrier("quarantined")

        expectMsgPF(10.seconds) {
          case ThisActorSystemQuarantinedEvent(_, _) =>
        }

        // check that we quarantine back
        val firstAssociation = RARP(system).provider.transport.asInstanceOf[ArteryTransport].association(firstAddress)
        awaitAssert {
          firstAssociation.associationState.isQuarantined(firstUid)
          firstAssociation.associationState.isQuarantined()
        }

        enterBarrier("still-quarantined")

        Await.result(system.whenTerminated, 10.seconds)

        val freshSystem = ActorSystem(
          system.name,
          ConfigFactory.parseString(s"""
              pekko.remote.artery.canonical.port = ${address.port.get}
              """).withFallback(system.settings.config))

        val probe = TestProbe()(freshSystem)

        freshSystem
          .actorSelection(RootActorPath(firstAddress) / "user" / "subject")
          .tell(Identify("subject"), probe.ref)
        probe.expectMsgType[ActorIdentity](5.seconds).ref should not be None

        // Now the other system will be able to pass, too
        freshSystem.actorOf(Props[Subject](), "subject")

        Await.ready(freshSystem.whenTerminated, 10.seconds)
      }

    }

  }
}

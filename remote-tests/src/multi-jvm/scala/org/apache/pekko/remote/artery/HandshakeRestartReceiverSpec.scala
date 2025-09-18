/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.remote.AddressUidExtension
import pekko.remote.RARP
import pekko.remote.UniqueAddress
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.testkit.STMultiNodeSpec
import pekko.testkit._

import com.typesafe.config.ConfigFactory

object HandshakeRestartReceiverSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
       pekko {
         loglevel = INFO
         actor.provider = remote
         remote.artery {
           enabled = on
         }
       }
       # test is using Java serialization and not priority to rewrite
       pekko.actor.allow-java-serialization = on
       pekko.actor.warn-about-java-serializer-usage = off
       """)))

  class Subject extends Actor {
    def receive = {
      case "shutdown" => context.system.terminate()
      case "identify" => sender() ! (AddressUidExtension(context.system).longAddressUid -> self)
    }
  }

}

class HandshakeRestartReceiverSpecMultiJvmNode1 extends HandshakeRestartReceiverSpec
class HandshakeRestartReceiverSpecMultiJvmNode2 extends HandshakeRestartReceiverSpec

abstract class HandshakeRestartReceiverSpec
    extends MultiNodeSpec(HandshakeRestartReceiverSpec)
    with STMultiNodeSpec
    with ImplicitSender {

  import HandshakeRestartReceiverSpec._

  override def initialParticipants = roles.size

  override def afterAll(): Unit = {
    super.afterAll()
  }

  def identifyWithUid(
      rootPath: ActorPath,
      actorName: String,
      timeout: FiniteDuration = remainingOrDefault): (Long, ActorRef) = {
    within(timeout) {
      system.actorSelection(rootPath / "user" / actorName) ! "identify"
      expectMsgType[(Long, ActorRef)]
    }
  }

  private def futureUniqueRemoteAddress(association: OutboundContext): Future[UniqueAddress] = {
    val p = Promise[UniqueAddress]()
    association.associationState.addUniqueRemoteAddressListener(a => p.success(a))
    p.future
  }

  "Artery Handshake" must {

    "detect restarted receiver and initiate new handshake" in {
      runOn(second) {
        system.actorOf(Props[Subject](), "subject")
      }
      enterBarrier("subject-started")

      runOn(first) {
        val secondRootPath = node(second)
        val (secondUid, _) = identifyWithUid(secondRootPath, "subject", 5.seconds)

        val secondAddress = node(second).address
        val secondAssociation = RARP(system).provider.transport.asInstanceOf[ArteryTransport].association(secondAddress)
        val secondUniqueRemoteAddress = Await.result(futureUniqueRemoteAddress(secondAssociation), 3.seconds)
        secondUniqueRemoteAddress.address should ===(secondAddress)
        secondUniqueRemoteAddress.uid should ===(secondUid)

        enterBarrier("before-shutdown")
        testConductor.shutdown(second).await

        within(30.seconds) {
          awaitAssert {
            identifyWithUid(secondRootPath, "subject2", 1.second)
          }
        }
        val (secondUid2, subject2) = identifyWithUid(secondRootPath, "subject2")
        secondUid2 should !==(secondUid)
        val secondUniqueRemoteAddress2 = Await.result(futureUniqueRemoteAddress(secondAssociation), 3.seconds)
        secondUniqueRemoteAddress2.uid should ===(secondUid2)
        secondUniqueRemoteAddress2.address should ===(secondAddress)
        secondUniqueRemoteAddress2 should !==(secondUniqueRemoteAddress)

        subject2 ! "shutdown"
      }

      runOn(second) {
        val address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        enterBarrier("before-shutdown")

        Await.result(system.whenTerminated, 10.seconds)

        val freshSystem = ActorSystem(
          system.name,
          ConfigFactory.parseString(s"""
              pekko.remote.artery.canonical.port = ${address.port.get}
              """).withFallback(system.settings.config))
        freshSystem.actorOf(Props[Subject](), "subject2")

        Await.result(freshSystem.whenTerminated, 45.seconds)
      }
    }

  }
}

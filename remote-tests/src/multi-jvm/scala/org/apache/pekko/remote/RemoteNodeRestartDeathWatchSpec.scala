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

package org.apache.pekko.remote

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.actor.Identify
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.ThrottlerTransportAdapter.Direction
import pekko.testkit._

class RemoteNodeRestartDeathWatchConfig(artery: Boolean) extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
      pekko.loglevel = INFO
      pekko.remote.log-remote-lifecycle-events = off
      pekko.remote.classic.transport-failure-detector.heartbeat-interval = 1 s
      pekko.remote.classic.transport-failure-detector.acceptable-heartbeat-pause = 3 s
      pekko.remote.artery.enabled = $artery
      pekko.remote.use-unsafe-remote-features-outside-cluster = on
    """)))

  testTransport(on = true)

}

class RemoteNodeRestartDeathWatchMultiJvmNode1
    extends RemoteNodeRestartDeathWatchSpec(new RemoteNodeRestartDeathWatchConfig(artery = false))
class RemoteNodeRestartDeathWatchMultiJvmNode2
    extends RemoteNodeRestartDeathWatchSpec(new RemoteNodeRestartDeathWatchConfig(artery = false))

// FIXME this is failing with Artery
//class ArteryRemoteNodeRestartDeathWatchMultiJvmNode1 extends RemoteNodeRestartDeathWatchSpec(
//  new RemoteNodeRestartDeathWatchConfig(artery = true))
//class ArteryRemoteNodeRestartDeathWatchMultiJvmNode2 extends RemoteNodeRestartDeathWatchSpec(
//  new RemoteNodeRestartDeathWatchConfig(artery = true))

object RemoteNodeRestartDeathWatchSpec {
  class Subject extends Actor {
    def receive = {
      case "shutdown" =>
        sender() ! "shutdown-ack"
        context.system.terminate()
      case msg => sender() ! msg
    }
  }
}

abstract class RemoteNodeRestartDeathWatchSpec(multiNodeConfig: RemoteNodeRestartDeathWatchConfig)
    extends RemotingMultiNodeSpec(multiNodeConfig) {
  import RemoteNodeRestartDeathWatchSpec._
  import multiNodeConfig._

  override def initialParticipants = roles.size

  def identify(role: RoleName, actorName: String): ActorRef = {
    system.actorSelection(node(role) / "user" / actorName) ! Identify(actorName)
    expectMsgType[ActorIdentity].ref.get
  }

  "RemoteNodeRestartDeathWatch" must {

    "receive Terminated when remote actor system is restarted" taggedAs LongRunningTest in {
      runOn(first) {
        val secondAddress = node(second).address
        enterBarrier("actors-started")

        val subject = identify(second, "subject")
        watch(subject)
        subject ! "hello"
        expectMsg("hello")
        enterBarrier("watch-established")

        // simulate a hard shutdown, nothing sent from the shutdown node
        testConductor.blackhole(second, first, Direction.Send).await
        testConductor.shutdown(second).await

        expectTerminated(subject, 15.seconds)

        within(5.seconds) {
          // retry because the Subject actor might not be started yet
          awaitAssert {
            system.actorSelection(RootActorPath(secondAddress) / "user" / "subject") ! "shutdown"
            expectMsg(1.second, "shutdown-ack")
          }
        }
      }

      runOn(second) {
        val address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        system.actorOf(Props[Subject](), "subject")
        enterBarrier("actors-started")

        enterBarrier("watch-established")

        Await.ready(system.whenTerminated, 30.seconds)

        val freshSystem = ActorSystem(
          system.name,
          ConfigFactory.parseString(s"""
          pekko.remote.classic.netty.tcp.port = ${address.port.get}
          pekko.remote.artery.canonical.port = ${address.port.get}
          """).withFallback(system.settings.config))
        freshSystem.actorOf(Props[Subject](), "subject")

        Await.ready(freshSystem.whenTerminated, 30.seconds)
      }

    }

  }
}

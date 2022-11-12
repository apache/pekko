/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import testkit.MultiNodeConfig

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.Identify
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.remote.transport.ThrottlerTransportAdapter.Direction
import pekko.testkit._

class AttemptSysMsgRedeliveryMultiJvmSpec(artery: Boolean) extends MultiNodeConfig {

  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
      akka.remote.artery.enabled = $artery
      akka.remote.use-unsafe-remote-features-outside-cluster = on
      """)).withFallback(RemotingMultiNodeSpec.commonConfig))

  testTransport(on = true)

}

class AttemptSysMsgRedeliveryMultiJvmNode1
    extends AttemptSysMsgRedeliverySpec(new AttemptSysMsgRedeliveryMultiJvmSpec(artery = false))
class AttemptSysMsgRedeliveryMultiJvmNode2
    extends AttemptSysMsgRedeliverySpec(new AttemptSysMsgRedeliveryMultiJvmSpec(artery = false))
class AttemptSysMsgRedeliveryMultiJvmNode3
    extends AttemptSysMsgRedeliverySpec(new AttemptSysMsgRedeliveryMultiJvmSpec(artery = false))

class ArteryAttemptSysMsgRedeliveryMultiJvmNode1
    extends AttemptSysMsgRedeliverySpec(new AttemptSysMsgRedeliveryMultiJvmSpec(artery = true))
class ArteryAttemptSysMsgRedeliveryMultiJvmNode2
    extends AttemptSysMsgRedeliverySpec(new AttemptSysMsgRedeliveryMultiJvmSpec(artery = true))
class ArteryAttemptSysMsgRedeliveryMultiJvmNode3
    extends AttemptSysMsgRedeliverySpec(new AttemptSysMsgRedeliveryMultiJvmSpec(artery = true))

object AttemptSysMsgRedeliverySpec {
  class Echo extends Actor {
    def receive = {
      case m => sender() ! m
    }
  }
}

abstract class AttemptSysMsgRedeliverySpec(multiNodeConfig: AttemptSysMsgRedeliveryMultiJvmSpec)
    extends RemotingMultiNodeSpec(multiNodeConfig) {
  import AttemptSysMsgRedeliverySpec._
  import multiNodeConfig._

  def initialParticipants = roles.size

  "AttemptSysMsgRedelivery" must {
    "redeliver system message after inactivity" taggedAs LongRunningTest in {
      system.actorOf(Props[Echo](), "echo")
      enterBarrier("echo-started")

      system.actorSelection(node(first) / "user" / "echo") ! Identify(None)
      val firstRef: ActorRef = expectMsgType[ActorIdentity].ref.get
      system.actorSelection(node(second) / "user" / "echo") ! Identify(None)
      val secondRef: ActorRef = expectMsgType[ActorIdentity].ref.get
      enterBarrier("refs-retrieved")

      runOn(first) {
        testConductor.blackhole(first, second, Direction.Both).await
      }
      enterBarrier("blackhole")

      runOn(first, third) {
        watch(secondRef)
      }
      runOn(second) {
        watch(firstRef)
      }
      enterBarrier("watch-established")

      runOn(first) {
        testConductor.passThrough(first, second, Direction.Both).await
      }
      enterBarrier("pass-through")

      system.actorSelection("/user/echo") ! PoisonPill

      runOn(first, third) {
        expectTerminated(secondRef, 10.seconds)
      }
      runOn(second) {
        expectTerminated(firstRef, 10.seconds)
      }

      enterBarrier("done")
    }
  }

}

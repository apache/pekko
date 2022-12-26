/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.classic

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.annotation.nowarn
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ ActorIdentity, Identify, _ }
import pekko.remote.{ RARP, RemotingMultiNodeSpec }
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.transport.AssociationHandle
import pekko.remote.transport.ThrottlerTransportAdapter.ForceDisassociateExplicitly
import pekko.testkit._

object RemoteGatePiercingSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false).withFallback(
      ConfigFactory.parseString("""
      pekko.loglevel = INFO
      pekko.remote.artery.enabled = false
      pekko.remote.classic.log-remote-lifecycle-events = INFO
      pekko.remote.classic.transport-failure-detector.acceptable-heartbeat-pause = 5 s
    """)))

  nodeConfig(first)(ConfigFactory.parseString("pekko.remote.classic.retry-gate-closed-for  = 1 d # Keep it long"))

  nodeConfig(second)(ConfigFactory.parseString("pekko.remote.classic.retry-gate-closed-for  = 1 s # Keep it short"))

  testTransport(on = true)

  class Subject extends Actor {
    def receive = {
      case "shutdown" => context.system.terminate()
    }
  }

}

class RemoteGatePiercingSpecMultiJvmNode1 extends RemoteGatePiercingSpec
class RemoteGatePiercingSpecMultiJvmNode2 extends RemoteGatePiercingSpec

@nowarn("msg=deprecated")
abstract class RemoteGatePiercingSpec extends RemotingMultiNodeSpec(RemoteGatePiercingSpec) {

  import RemoteGatePiercingSpec._

  override def initialParticipants = 2

  def identify(role: RoleName, actorName: String): ActorRef = {
    system.actorSelection(node(role) / "user" / actorName) ! Identify(actorName)
    expectMsgType[ActorIdentity].ref.get
  }

  "RemoteGatePiercing" must {

    "allow restarted node to pass through gate" taggedAs LongRunningTest in {
      system.actorOf(Props[Subject](), "subject")
      enterBarrier("actors-started")

      runOn(first) {
        identify(second, "subject")

        enterBarrier("actors-communicate")

        EventFilter.warning(pattern = "address is now gated", occurrences = 1).intercept {
          Await.result(
            RARP(system).provider.transport
              .managementCommand(ForceDisassociateExplicitly(node(second).address, AssociationHandle.Unknown)),
            3.seconds)
        }

        enterBarrier("gated")

        enterBarrier("gate-pierced")

      }

      runOn(second) {
        enterBarrier("actors-communicate")

        enterBarrier("gated")

        // Pierce the gate
        within(30.seconds) {
          awaitAssert {
            identify(first, "subject")
          }
        }

        enterBarrier("gate-pierced")

      }

    }

  }
}

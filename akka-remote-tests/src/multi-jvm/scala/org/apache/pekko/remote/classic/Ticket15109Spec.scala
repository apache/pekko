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

object Ticket15109Spec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")

  commonConfig(
    debugConfig(on = false).withFallback(
      ConfigFactory.parseString("""
      pekko.loglevel = INFO
      pekko.remote.artery.enabled = off
      pekko.remote.classic.log-remote-lifecycle-events = INFO
      ## Keep it tight, otherwise reestablishing a connection takes too much time
      pekko.remote.classic.transport-failure-detector.heartbeat-interval = 1 s
      pekko.remote.classic.transport-failure-detector.acceptable-heartbeat-pause = 3 s
      pekko.remote.classic.retry-gate-closed-for = 0.5 s
                              """)))

  testTransport(on = true)

  class Subject extends Actor {
    def receive = {
      case "ping" => sender() ! "pong"
    }
  }

}

class Ticket15109SpecMultiJvmNode1 extends Ticket15109Spec
class Ticket15109SpecMultiJvmNode2 extends Ticket15109Spec

@nowarn("msg=deprecated")
abstract class Ticket15109Spec extends RemotingMultiNodeSpec(Ticket15109Spec) {

  import Ticket15109Spec._

  override def initialParticipants = roles.size

  def identify(role: RoleName, actorName: String): ActorRef = {
    system.actorSelection(node(role) / "user" / actorName) ! Identify(0)
    expectMsgType[ActorIdentity](5.seconds).getActorRef.get
  }

  def ping(ref: ActorRef) = {
    within(30.seconds) {
      awaitAssert {
        ref ! "ping"
        expectMsg(1.second, "pong")
      }
    }
  }

  "Quarantining" must {

    "not be introduced during normal errors (regression #15109)" taggedAs LongRunningTest in {
      var subject: ActorRef = system.deadLetters

      runOn(second) {
        system.actorOf(Props[Subject](), "subject")
      }

      enterBarrier("actors-started")

      runOn(first) {
        // Acquire ActorRef from first system
        subject = identify(second, "subject")
      }

      enterBarrier("actor-identified")

      runOn(second) {
        // Force a disassociation. Using the message Shutdown, which is suboptimal here, but this is the only
        // DisassociateInfo that triggers the code-path we want to test
        Await.result(
          RARP(system).provider.transport
            .managementCommand(ForceDisassociateExplicitly(node(first).address, AssociationHandle.Shutdown)),
          3.seconds)
      }

      enterBarrier("disassociated")

      runOn(first) {
        ping(subject)
      }

      enterBarrier("done")

    }

  }
}

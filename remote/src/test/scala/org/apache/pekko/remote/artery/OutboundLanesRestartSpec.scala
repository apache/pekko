/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.RootActorPath
import pekko.remote.RARP
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

import org.scalatest.concurrent.Eventually
import org.scalatest.time.Span

object OutboundLanesRestartSpec {
  val OutboundLanes = 4
}

class OutboundLanesRestartSpec extends ArteryMultiNodeSpec(s"""
  pekko.loglevel = INFO
  pekko.remote.artery.advanced {
    outbound-lanes = ${OutboundLanesRestartSpec.OutboundLanes}
    stop-idle-outbound-after = 1 s
    connection-timeout = 2 s
  }
  """) with ImplicitSender with Eventually {
  import OutboundLanesRestartSpec._

  override implicit val patience: PatienceConfig = {
    import pekko.testkit.TestDuration
    PatienceConfig(testKitSettings.DefaultTimeout.duration.dilated * 2, Span(200, org.scalatest.time.Millis))
  }

  private def laneOf(ref: ActorRef): Int = math.abs(ref.path.uid % OutboundLanes)

  private def assertOrdinaryLanesActive(association: Association, expected: Boolean): Unit =
    (0 until OutboundLanes).foreach { i =>
      withClue(s"ordinary lane [$i]:") {
        association.isStreamActive(Association.OrdinaryQueueIndex + i) shouldBe expected
      }
    }

  "Outbound ordinary streams with several lanes" should {

    "be stopped when idle and restarted by a message routed to a lane other than the first" in {
      val remoteSystem = newRemoteSystem()
      try {
        val remoteAddress = RARP(remoteSystem).provider.getDefaultAddress
        // the outbound lane is selected by the recipient uid, so use an echo actor that is not on the first lane
        val remoteEchoName = Iterator
          .from(0)
          .map(i => remoteSystem.actorOf(TestActors.echoActorProps, s"echo-$i"))
          .find(laneOf(_) != 0)
          .get
          .path
          .name

        val echoRef = system
          .actorSelection(RootActorPath(remoteAddress) / "user" / remoteEchoName)
          .resolveOne(remainingOrDefault)
          .futureValue
        laneOf(echoRef) should !==(0)

        val localProbe = TestProbe()
        echoRef.tell("ping", localProbe.ref)
        localProbe.expectMsg("ping")

        val association = RARP(system).provider.transport.asInstanceOf[ArteryTransport].association(remoteAddress)
        assertOrdinaryLanesActive(association, expected = true)

        eventually {
          assertOrdinaryLanesActive(association, expected = false)
        }

        // the first message after the stop is routed to a lane other than the first, it must restart the stream
        echoRef.tell("ping2", localProbe.ref)
        localProbe.expectMsg("ping2")
        assertOrdinaryLanesActive(association, expected = true)
      } finally {
        shutdown(remoteSystem)
      }
    }
  }
}

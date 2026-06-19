/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.remote.artery

import java.nio.ByteBuffer

import org.apache.pekko
import pekko.actor.SelectChildName
import pekko.actor.SelectionPathElement
import pekko.protobufv3.internal.ByteString
import pekko.remote.ContainerFormats
import pekko.remote.ContainerFormats.PatternType

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActorSelectionQueueDistributionSpec extends AnyWordSpec with Matchers {

  private val OrdinaryQueueIndex = 2

  private def computeOutboundQueueIndex(elements: Seq[SelectionPathElement], outboundLanes: Int): Int = {
    if (outboundLanes == 1) OrdinaryQueueIndex
    else OrdinaryQueueIndex + ((elements.hashCode() & Int.MaxValue) % outboundLanes)
  }

  private def selectionElements(names: String*): Seq[SelectionPathElement] =
    names.map(SelectChildName.apply)

  private def selectionEnvelope(names: String*): ContainerFormats.SelectionEnvelope = {
    val builder = ContainerFormats.SelectionEnvelope
      .newBuilder()
      .setEnclosedMessage(ByteString.EMPTY)
      .setSerializerId(0)

    names.foreach { name =>
      builder.addPattern(
        ContainerFormats.Selection
          .newBuilder()
          .setType(PatternType.CHILD_NAME)
          .setMatcher(name))
    }

    builder.build()
  }

  private def computeInboundLane(names: Seq[String], inboundLanes: Int, originUid: Long): Int = {
    val selectionHash = ArteryTransport.actorSelectionMessagePathHash(selectionEnvelope(names: _*))
    math.abs(ArteryTransport.inboundLanePartitionHash(selectionHash, originUid) % inboundLanes)
  }

  "ActorSelection queue distribution" must {

    "distribute different outbound target paths across lanes" in {
      val outboundLanes = 3
      val paths = (1 to 100).map(i => selectionElements("user", s"echo$i"))
      val queueIndices = paths.map(p => computeOutboundQueueIndex(p, outboundLanes))
      val distinctLanes = queueIndices.distinct

      distinctLanes.size should be > 1
      distinctLanes.foreach { idx =>
        idx should be >= OrdinaryQueueIndex
        idx should be < (OrdinaryQueueIndex + outboundLanes)
      }
    }

    "produce non-negative queue indices even for negative hash codes" in {
      val outboundLanes = 3
      val paths = (1 to 1000).map(i => selectionElements("user", s"actor$i"))
      val queueIndices = paths.map(p => computeOutboundQueueIndex(p, outboundLanes))

      queueIndices.foreach { idx =>
        idx should be >= OrdinaryQueueIndex
      }
    }

    "preserve ordering for same target path" in {
      val outboundLanes = 3
      val path = selectionElements("user", "echoA")
      val indices = (1 to 10).map(_ => computeOutboundQueueIndex(path, outboundLanes))

      indices.distinct.size should be(1)
    }

    "use single lane when outboundLanes is 1" in {
      val paths = (1 to 10).map(i => selectionElements("user", s"echo$i"))
      val queueIndices = paths.map(p => computeOutboundQueueIndex(p, 1))

      queueIndices.distinct should be(Seq(OrdinaryQueueIndex))
    }

    "not concentrate all paths on lane 0 (original bug)" in {
      val outboundLanes = 3
      val paths = Seq(
        selectionElements("user", "echoA2"),
        selectionElements("user", "echoB2"),
        selectionElements("user", "echoC2"))

      val queueIndices = paths.map(p => computeOutboundQueueIndex(p, outboundLanes))
      val laneOffsets = queueIndices.map(_ - OrdinaryQueueIndex)

      laneOffsets should not be Seq(0, 0, 0)
    }

    "distribute inbound ActorSelection messages by selected target path" in {
      val inboundLanes = 3
      val originUid = 17L
      val paths = (1 to 100).map(i => Seq("user", s"echo$i"))
      val lanes = paths.map(path => computeInboundLane(path, inboundLanes, originUid))

      lanes.distinct.size should be > 1
    }

    "not concentrate inbound ActorSelection messages on the root guardian lane" in {
      val inboundLanes = 3
      val originUid = 17L
      val paths = Seq(
        Seq("user", "echoA2"),
        Seq("user", "echoB2"),
        Seq("user", "echoC2"))
      val lanes = paths.map(path => computeInboundLane(path, inboundLanes, originUid))
      val rootGuardianLane =
        math.abs(ArteryTransport.inboundLanePartitionHash(destinationHash = 0, originUid) % inboundLanes)

      lanes should not be paths.map(_ => rootGuardianLane)
    }

    "preserve inbound ordering for same selected target path and origin" in {
      val inboundLanes = 3
      val originUid = 17L
      val path = Seq("user", "echoA2")
      val lanes = (1 to 10).map(_ => computeInboundLane(path, inboundLanes, originUid))

      lanes.distinct.size should be(1)
    }

    "parse ActorSelection path hash without moving the envelope buffer position" in {
      val bytes = selectionEnvelope("user", "echoA2").toByteArray
      val byteBuffer = ByteBuffer.allocate(bytes.length + 4)
      byteBuffer.putInt(17)
      byteBuffer.put(bytes)
      byteBuffer.flip()
      byteBuffer.position(4)

      val positionBefore = byteBuffer.position()
      ArteryTransport.actorSelectionMessagePathHash(byteBuffer).isDefined shouldBe true

      byteBuffer.position() shouldBe positionBefore
    }

    "ignore invalid ActorSelection envelopes when computing path hash" in {
      val byteBuffer = ByteBuffer.wrap(Array[Byte](1, 2, 3))

      ArteryTransport.actorSelectionMessagePathHash(byteBuffer) shouldBe None
    }
  }
}

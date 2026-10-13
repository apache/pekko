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
import pekko.actor.SelectChildPattern
import pekko.actor.SelectParent
import pekko.actor.SelectionPathElement
import pekko.protobufv3.internal.ByteString
import pekko.protobufv3.internal.UnknownFieldSet
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

  private def selectionEnvelopeFromElements(elements: SelectionPathElement*): ContainerFormats.SelectionEnvelope = {
    val builder = ContainerFormats.SelectionEnvelope
      .newBuilder()
      .setEnclosedMessage(ByteString.EMPTY)
      .setSerializerId(0)

    elements.foreach {
      case SelectChildName(name) =>
        builder.addPattern(
          ContainerFormats.Selection
            .newBuilder()
            .setType(PatternType.CHILD_NAME)
            .setMatcher(name))
      case SelectChildPattern(patternStr) =>
        builder.addPattern(
          ContainerFormats.Selection
            .newBuilder()
            .setType(PatternType.CHILD_PATTERN)
            .setMatcher(patternStr))
      case SelectParent =>
        builder.addPattern(
          ContainerFormats.Selection
            .newBuilder()
            .setType(PatternType.PARENT))
    }

    builder.build()
  }

  private def unknownField(fieldNumber: Int): UnknownFieldSet =
    UnknownFieldSet
      .newBuilder()
      .addField(fieldNumber, UnknownFieldSet.Field.newBuilder().addVarint(17L).build())
      .build()

  private def selectionEnvelopeWithUnknownFields(names: String*): ContainerFormats.SelectionEnvelope = {
    val builder = ContainerFormats.SelectionEnvelope
      .newBuilder()
      .setEnclosedMessage(ByteString.EMPTY)
      .setSerializerId(0)
      .setUnknownFields(unknownField(42))

    names.foreach { name =>
      builder.addPattern(
        ContainerFormats.Selection
          .newBuilder()
          .setType(PatternType.CHILD_NAME)
          .setMatcher(name)
          .setUnknownFields(unknownField(43)))
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
      val envelope = selectionEnvelope("user", "echoA2")
      val bytes = envelope.toByteArray
      val byteBuffer = ByteBuffer.allocate(bytes.length + 4)
      byteBuffer.putInt(17)
      byteBuffer.put(bytes)
      byteBuffer.flip()
      byteBuffer.position(4)

      val positionBefore = byteBuffer.position()
      ArteryTransport.actorSelectionMessagePathHash(byteBuffer) shouldBe Some(
        ArteryTransport.actorSelectionMessagePathHash(envelope))

      byteBuffer.position() shouldBe positionBefore
    }

    "skip unknown fields when parsing ActorSelection path hash" in {
      val envelope = selectionEnvelopeWithUnknownFields("user", "echoA2")
      val byteBuffer = ByteBuffer.wrap(envelope.toByteArray)

      ArteryTransport.actorSelectionMessagePathHash(byteBuffer) shouldBe Some(
        ArteryTransport.actorSelectionMessagePathHash(envelope))
    }

    "ignore invalid ActorSelection envelopes when computing path hash" in {
      val byteBuffer = ByteBuffer.wrap(Array[Byte](1, 2, 3))

      ArteryTransport.actorSelectionMessagePathHash(byteBuffer) shouldBe None
    }

    "produce consistent hash for SelectParent elements between ByteBuffer and SelectionEnvelope parsing" in {
      val envelope = selectionEnvelopeFromElements(SelectChildName("user"), SelectParent, SelectChildName("echo"))
      val byteBuffer = ByteBuffer.wrap(envelope.toByteArray)

      ArteryTransport.actorSelectionMessagePathHash(byteBuffer) shouldBe Some(
        ArteryTransport.actorSelectionMessagePathHash(envelope))
    }

    "produce consistent hash for SelectChildPattern elements between ByteBuffer and SelectionEnvelope parsing" in {
      val envelope =
        selectionEnvelopeFromElements(SelectChildName("user"), SelectChildPattern("echo*"))
      val byteBuffer = ByteBuffer.wrap(envelope.toByteArray)

      ArteryTransport.actorSelectionMessagePathHash(byteBuffer) shouldBe Some(
        ArteryTransport.actorSelectionMessagePathHash(envelope))
    }

    "produce distinct hashes for different SelectionPathElement types" in {
      val childNameEnvelope = selectionEnvelopeFromElements(SelectChildName("user"), SelectChildName("echo"))
      val selectParentEnvelope =
        selectionEnvelopeFromElements(SelectChildName("user"), SelectParent, SelectChildName("echo"))
      val childPatternEnvelope =
        selectionEnvelopeFromElements(SelectChildName("user"), SelectChildPattern("echo*"))

      val childNameHash = ArteryTransport.actorSelectionMessagePathHash(childNameEnvelope)
      val selectParentHash = ArteryTransport.actorSelectionMessagePathHash(selectParentEnvelope)
      val childPatternHash = ArteryTransport.actorSelectionMessagePathHash(childPatternEnvelope)

      childNameHash should not be selectParentHash
      childNameHash should not be childPatternHash
      selectParentHash should not be childPatternHash
    }

    "distribute paths with SelectParent across inbound lanes" in {
      val inboundLanes = 3
      val originUid = 42L
      val elements = Seq(
        Seq[SelectionPathElement](SelectChildName("user"), SelectParent, SelectChildName("echoA")),
        Seq[SelectionPathElement](SelectChildName("user"), SelectParent, SelectChildName("echoB")),
        Seq[SelectionPathElement](SelectChildName("user"), SelectParent, SelectChildName("echoC")))

      val lanes = elements.map { elems =>
        val envelope = selectionEnvelopeFromElements(elems: _*)
        val selectionHash = ArteryTransport.actorSelectionMessagePathHash(envelope)
        math.abs(ArteryTransport.inboundLanePartitionHash(selectionHash, originUid) % inboundLanes)
      }

      lanes.distinct.size should be > 1
    }
  }
}

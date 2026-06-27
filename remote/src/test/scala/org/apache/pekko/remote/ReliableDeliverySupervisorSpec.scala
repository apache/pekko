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

package org.apache.pekko.remote

import java.util.concurrent.ConcurrentHashMap

import scala.annotation.nowarn
import scala.concurrent.Promise

import org.apache.pekko
import pekko.actor.{ ActorRef, Address, Nobody, RootActorPath }
import pekko.remote.EndpointManager.{ Link, ResendState, Send }
import pekko.remote.ReliableDeliverySupervisor.AckFromReader
import pekko.remote.transport._
import pekko.testkit.{ ImplicitSender, PekkoSpec, TestActorRef, TestProbe }
import pekko.util.OptionVal

import com.typesafe.config.ConfigFactory

object ReliableDeliverySupervisorSpec {
  val config = ConfigFactory.parseString("""
    pekko {
      actor.provider = remote
      actor.allow-java-serialization = on
      actor.warn-about-java-serializer-usage = off

      remote {
        artery.enabled = off
        warn-about-direct-use = off
        use-unsafe-remote-features-outside-cluster = on

        classic {
          resend-interval = 30 s
          system-message-buffer-size = 10
          system-message-ack-piggyback-timeout = 30 s

          netty.tcp {
            hostname = "localhost"
            port = 0
          }
        }
      }
    }
  """)
}

@nowarn("msg=deprecated")
class ReliableDeliverySupervisorSpec extends PekkoSpec(ReliableDeliverySupervisorSpec.config) with ImplicitSender {

  private val localAddress = Address("pekko.test", system.name, "localhost", 2551)
  private val remoteAddress = Address("pekko.test", "remote-system", "localhost", 2552)
  private val oldUid = 1
  private val newUid = 2

  "ReliableDeliverySupervisor" must {
    "ignore ACKs from stale EndpointReader UIDs after a new UID is confirmed" in {
      val parentProbe = TestProbe()
      val supervisor = newSupervisor(initialUid = oldUid, parentProbe.ref)
      val underlying = supervisor.underlyingActor

      supervisor ! ReliableDeliverySupervisor.GotUid(newUid, remoteAddress)
      parentProbe.expectMsg(ReliableDeliverySupervisor.GotUid(newUid, remoteAddress))
      underlying.resendBuffer = bufferWith(0L, 1L)

      supervisor ! AckFromReader(oldUid, Ack(SeqNo(0)))

      underlying.resendBuffer.nonAcked.map(_.seq) should ===(Vector(SeqNo(0), SeqNo(1)))

      supervisor ! Ack(SeqNo(0))

      underlying.resendBuffer.nonAcked.map(_.seq) should ===(Vector(SeqNo(0), SeqNo(1)))

      supervisor ! AckFromReader(newUid, Ack(SeqNo(0)))

      underlying.resendBuffer.nonAcked.map(_.seq) should ===(Vector(SeqNo(1)))
      underlying.resendBuffer = new AckedSendBuffer[Send](0)
    }
  }

  private def newSupervisor(initialUid: Int, parent: ActorRef): TestActorRef[ReliableDeliverySupervisor] = {
    val registry = new TestTransport.AssociationRegistry
    val underlyingTransport =
      new TestTransport(localAddress.copy(protocol = "test"), registry, schemeIdentifier = "test")
    underlyingTransport.writeBehavior.pushConstant(true)
    val protocolTransport = new PekkoProtocolTransport(
      underlyingTransport,
      system,
      new PekkoProtocolSettings(system.settings.config),
      PekkoPduProtobufCodec)
    val underlyingHandle =
      TestAssociationHandle(localAddress.copy(protocol = "test"), remoteAddress.copy(protocol = "test"),
        underlyingTransport, inbound = true)
    val handle = new PekkoProtocolHandle(
      localAddress,
      remoteAddress,
      Promise[AssociationHandle.HandleEventListener](),
      underlyingHandle,
      HandshakeInfo(remoteAddress, initialUid),
      TestProbe().ref,
      PekkoPduProtobufCodec,
      RARP(system).provider.remoteSettings.ProtocolName)

    TestActorRef[ReliableDeliverySupervisor](
      ReliableDeliverySupervisor.props(
        handleOrActive = Some(handle),
        localAddress,
        remoteAddress,
        refuseUid = None,
        protocolTransport,
        RARP(system).provider.remoteSettings,
        PekkoPduProtobufCodec,
        new ConcurrentHashMap[Link, ResendState]),
      parent)
  }

  private def bufferWith(seqNumbers: Long*): AckedSendBuffer[Send] =
    seqNumbers.foldLeft(new AckedSendBuffer[Send](10)) { (buffer, seq) =>
      buffer.buffer(send(seq))
    }

  private def send(seq: Long): Send =
    Send(
      message = s"msg-$seq",
      senderOption = OptionVal.None,
      recipient = remoteRef,
      seqOpt = Some(SeqNo(seq)))

  private def remoteRef: RemoteActorRef =
    new RemoteActorRef(
      RARP(system).provider.transport,
      localAddress,
      RootActorPath(remoteAddress) / "user" / "recipient",
      Nobody,
      props = None,
      deploy = None,
      acceptProtocolNames = Set("pekko"))
}

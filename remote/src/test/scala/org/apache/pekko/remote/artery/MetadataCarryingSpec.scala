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

import java.nio.{ ByteBuffer, CharBuffer }
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

import org.apache.pekko
import pekko.actor._
import pekko.testkit.ImplicitSender
import pekko.testkit.JavaSerializable
import pekko.testkit.TestActors
import pekko.testkit.TestProbe
import pekko.util.ByteString

object MetadataCarryingSpy extends ExtensionId[MetadataCarryingSpy] with ExtensionIdProvider {
  override def get(system: ActorSystem): MetadataCarryingSpy = super.get(system)
  override def get(system: ClassicActorSystemProvider): MetadataCarryingSpy = super.get(system)
  override def lookup = MetadataCarryingSpy
  override def createExtension(system: ExtendedActorSystem): MetadataCarryingSpy = new MetadataCarryingSpy

  final case class RemoteMessageSent(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long)
  final case class RemoteMessageReceived(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long)
  final case class RemoteWriteMetadata(recipient: ActorRef, message: Object, sender: ActorRef)
  final case class RemoteReadMetadata(recipient: ActorRef, message: Object, sender: ActorRef, metadata: String)
}

class MetadataCarryingSpy extends Extension {
  def ref: Option[ActorRef] = Option(_ref.get())
  def setProbe(bs: ActorRef): Unit = _ref.set(bs)
  private[this] val _ref = new AtomicReference[ActorRef]()
}

class TestInstrument(system: ExtendedActorSystem) extends RemoteInstrument {
  import pekko.remote.artery.MetadataCarryingSpy._

  private val charset = Charset.forName("UTF-8")
  private val encoder = charset.newEncoder()
  private val decoder = charset.newDecoder()

  override val identifier: Byte = 1

  override def serializationTimingEnabled: Boolean = true

  override def remoteWriteMetadata(recipient: ActorRef, message: Object, sender: ActorRef, buffer: ByteBuffer): Unit =
    message match {
      case _: MetadataCarryingSpec.Ping | ActorSelectionMessage(_: MetadataCarryingSpec.Ping, _, _) =>
        val metadata = "!!!"
        buffer.putInt(metadata.length)
        encoder.encode(CharBuffer.wrap(metadata), buffer, true)
        encoder.flush(buffer)
        encoder.reset()
        MetadataCarryingSpy(system).ref.foreach(_ ! RemoteWriteMetadata(recipient, message, sender))
      case _ =>
    }

  override def remoteReadMetadata(recipient: ActorRef, message: Object, sender: ActorRef, buffer: ByteBuffer): Unit =
    message match {
      case _: MetadataCarryingSpec.Ping | ActorSelectionMessage(_: MetadataCarryingSpec.Ping, _, _) =>
        val size = buffer.getInt
        val charBuffer = CharBuffer.allocate(size)
        decoder.decode(buffer, charBuffer, false)
        decoder.reset()
        charBuffer.flip()
        val metadata = charBuffer.toString
        MetadataCarryingSpy(system).ref.foreach(_ ! RemoteReadMetadata(recipient, message, sender, metadata))
      case _ =>
    }

  override def remoteMessageSent(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long): Unit =
    message match {
      case _: MetadataCarryingSpec.Ping | ActorSelectionMessage(_: MetadataCarryingSpec.Ping, _, _) =>
        MetadataCarryingSpy(system).ref.foreach(_ ! RemoteMessageSent(recipient, message, sender, size, time))
      case _ =>
    }

  override def remoteMessageReceived(
      recipient: ActorRef,
      message: Object,
      sender: ActorRef,
      size: Int,
      time: Long): Unit =
    message match {
      case _: MetadataCarryingSpec.Ping | ActorSelectionMessage(_: MetadataCarryingSpec.Ping, _, _) =>
        MetadataCarryingSpy(system).ref.foreach(_ ! RemoteMessageReceived(recipient, message, sender, size, time))
      case _ =>
    }
}

object MetadataCarryingSpec {
  final case class Ping(payload: ByteString = ByteString.empty) extends JavaSerializable

  class ProxyActor(local: ActorRef, remotePath: ActorPath) extends Actor {
    val remote = context.system.actorSelection(remotePath)
    override def receive = {
      case message if sender() == local => remote ! message
      case message                      => local ! message
    }
  }
}

class MetadataCarryingSpec extends ArteryMultiNodeSpec("""
    pekko {
      remote.artery.advanced {
        instruments = [ "org.apache.pekko.remote.artery.TestInstrument" ]
      }
    }
  """) with ImplicitSender {

  import MetadataCarryingSpec._
  import MetadataCarryingSpy._

  "Metadata" should {

    "be included in remote messages" in {
      val systemA = localSystem
      val systemB = newRemoteSystem(name = Some("systemB"))

      val instrumentProbeA = TestProbe()(systemA)
      MetadataCarryingSpy(systemA).setProbe(instrumentProbeA.ref)
      val instrumentProbeB = TestProbe()(systemB)
      MetadataCarryingSpy(systemB).setProbe(instrumentProbeB.ref)

      systemB.actorOf(TestActors.echoActorProps, "reply")
      val proxyA = systemA.actorOf(Props(classOf[ProxyActor], testActor, rootActorPath(systemB) / "user" / "reply"))
      proxyA ! Ping()
      expectMsgType[Ping]

      instrumentProbeA.expectMsgType[RemoteWriteMetadata]
      val sentA = instrumentProbeA.expectMsgType[RemoteMessageSent]
      val readB = instrumentProbeB.expectMsgType[RemoteReadMetadata]
      val recvdB = instrumentProbeB.expectMsgType[RemoteMessageReceived]
      readB.metadata should ===("!!!")
      sentA.size should be > 0
      sentA.time should be > 0L
      recvdB.size should ===(sentA.size)
      recvdB.time should be > 0L

      // for the reply
      instrumentProbeB.expectMsgType[RemoteWriteMetadata]
      val sentB = instrumentProbeB.expectMsgType[RemoteMessageSent]
      val readA = instrumentProbeA.expectMsgType[RemoteReadMetadata]
      val recvdA = instrumentProbeA.expectMsgType[RemoteMessageReceived]
      readA.metadata should ===("!!!")
      sentB.size should be > 0
      sentB.time should be > 0L
      recvdA.size should ===(sentB.size)
      recvdA.time should be > 0L
    }
  }

}

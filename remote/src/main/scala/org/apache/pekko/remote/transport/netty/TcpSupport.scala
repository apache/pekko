/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.transport.netty

import java.net.InetSocketAddress

import scala.concurrent.{ Future, Promise }

import scala.annotation.nowarn
import org.jboss.netty.buffer.{ ChannelBuffer, ChannelBuffers }
import org.jboss.netty.channel._

import org.apache.pekko
import pekko.actor.Address
import pekko.event.LoggingAdapter
import pekko.remote.transport.AssociationHandle
import pekko.remote.transport.AssociationHandle.{ Disassociated, HandleEvent, HandleEventListener, InboundPayload }
import pekko.remote.transport.Transport.AssociationEventListener
import pekko.util.ByteString

/**
 * INTERNAL API
 */
private[remote] object ChannelLocalActor extends ChannelLocal[Option[HandleEventListener]] {
  override def initialValue(channel: Channel): Option[HandleEventListener] = None
  def notifyListener(channel: Channel, msg: HandleEvent): Unit = get(channel).foreach { _.notify(msg) }
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] trait TcpHandlers extends CommonHandlers {
  protected def log: LoggingAdapter

  import ChannelLocalActor._

  override def registerListener(
      channel: Channel,
      listener: HandleEventListener,
      msg: ChannelBuffer,
      remoteSocketAddress: InetSocketAddress): Unit =
    ChannelLocalActor.set(channel, Some(listener))

  override def createHandle(channel: Channel, localAddress: Address, remoteAddress: Address): AssociationHandle =
    new TcpAssociationHandle(localAddress, remoteAddress, transport, channel)

  override def onDisconnect(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    notifyListener(e.getChannel, Disassociated(AssociationHandle.Unknown))
    log.debug("Remote connection to [{}] was disconnected because of {}", e.getChannel.getRemoteAddress, e)
  }

  override def onMessage(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    val bytes: Array[Byte] = e.getMessage.asInstanceOf[ChannelBuffer].array()
    if (bytes.length > 0) notifyListener(e.getChannel, InboundPayload(ByteString(bytes)))
  }

  override def onException(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    notifyListener(e.getChannel, Disassociated(AssociationHandle.Unknown))
    log.warning("Remote connection to [{}] failed with {}", e.getChannel.getRemoteAddress, e.getCause)
    e.getChannel.close() // No graceful close here
  }
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] class TcpServerHandler(
    _transport: NettyTransport,
    _associationListenerFuture: Future[AssociationEventListener],
    val log: LoggingAdapter)
    extends ServerHandler(_transport, _associationListenerFuture)
    with TcpHandlers {

  override def onConnect(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit =
    initInbound(e.getChannel, e.getChannel.getRemoteAddress, null)

}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] class TcpClientHandler(_transport: NettyTransport, remoteAddress: Address, val log: LoggingAdapter)
    extends ClientHandler(_transport, remoteAddress)
    with TcpHandlers {

  override def onConnect(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit =
    initOutbound(e.getChannel, e.getChannel.getRemoteAddress, null)

}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] class TcpAssociationHandle(
    val localAddress: Address,
    val remoteAddress: Address,
    val transport: NettyTransport,
    private val channel: Channel)
    extends AssociationHandle {
  import transport.executionContext

  override val readHandlerPromise: Promise[HandleEventListener] = Promise()

  override def write(payload: ByteString): Boolean =
    if (channel.isWritable && channel.isOpen) {
      channel.write(ChannelBuffers.wrappedBuffer(payload.asByteBuffer))
      true
    } else false

  override def disassociate(): Unit = NettyTransport.gracefulClose(channel)
}

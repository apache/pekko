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

package org.apache.pekko.remote.transport.netty

import java.net.InetSocketAddress

import scala.annotation.nowarn
import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.actor.Address
import pekko.event.LoggingAdapter
import pekko.remote.transport.AssociationHandle
import pekko.remote.transport.AssociationHandle.{ Disassociated, HandleEvent, HandleEventListener, InboundPayload }
import pekko.remote.transport.Transport.AssociationEventListener
import pekko.util.ByteString

import io.netty.buffer.Unpooled
import io.netty.channel.{ Channel, ChannelHandlerContext }
import io.netty.util.AttributeKey

private[remote] object TcpHandlers {
  private val LISTENER = AttributeKey.valueOf[HandleEventListener]("listener")
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] trait TcpHandlers extends CommonHandlers {
  protected def log: LoggingAdapter
  import TcpHandlers._

  override def registerListener(
      channel: Channel,
      listener: HandleEventListener,
      remoteSocketAddress: InetSocketAddress): Unit = channel.attr(LISTENER).set(listener)

  override def createHandle(channel: Channel, localAddress: Address, remoteAddress: Address): AssociationHandle =
    new TcpAssociationHandle(localAddress, remoteAddress, transport, channel)

  override def onDisconnect(ctx: ChannelHandlerContext): Unit = {
    notifyListener(ctx.channel(), Disassociated(AssociationHandle.Unknown))
    log.debug("Remote connection to [{}] was disconnected.", ctx.channel().remoteAddress())
  }

  override def onMessage(ctx: ChannelHandlerContext, bytes: Array[Byte]): Unit = {
    if (bytes.length > 0) notifyListener(ctx.channel(), InboundPayload(ByteString(bytes)))
  }

  override def onException(ctx: ChannelHandlerContext, e: Throwable): Unit = {
    notifyListener(ctx.channel(), Disassociated(AssociationHandle.Unknown))
    log.warning("Remote connection to [{}] failed with {}", ctx.channel().remoteAddress(), e.getCause)
    ctx.channel().close() // No graceful close here
  }

  private def notifyListener(channel: Channel, event: HandleEvent): Unit = {
    val listener = channel.attr(LISTENER).get()
    if (listener ne null) {
      listener.notify(event)
    }
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

  override def onConnect(ctx: ChannelHandlerContext): Unit =
    initInbound(ctx.channel(), ctx.channel().remoteAddress())

}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] class TcpClientHandler(_transport: NettyTransport, remoteAddress: Address, val log: LoggingAdapter)
    extends ClientHandler(_transport, remoteAddress)
    with TcpHandlers {

  override def onConnect(ctx: ChannelHandlerContext): Unit =
    initOutbound(ctx.channel(), ctx.channel().remoteAddress())

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
      channel.writeAndFlush(Unpooled.wrappedBuffer(payload.asByteBuffer))
      true
    } else false

  override def disassociate(): Unit = NettyTransport.gracefulClose(channel)
}

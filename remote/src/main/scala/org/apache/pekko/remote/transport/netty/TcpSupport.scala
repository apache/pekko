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

import io.netty.buffer.{ ByteBuf, ByteBufUtil }
import io.netty.channel.{ Channel, ChannelHandlerContext }
import io.netty.util.{ AttributeKey, ReferenceCountUtil }

/**
 * INTERNAL API
 */
private[remote] object TcpHandlers {
  private val LISTENER = AttributeKey.valueOf[Option[HandleEventListener]]("HandleEventListener")
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
      msg: ByteBuf,
      remoteSocketAddress: InetSocketAddress): Unit = {
    channel.attr(LISTENER).set(Some(listener))
  }

  override def createHandle(channel: Channel, localAddress: Address, remoteAddress: Address): AssociationHandle =
    new TcpAssociationHandle(localAddress, remoteAddress, transport, channel)

  override protected def onInactive(ctx: ChannelHandlerContext): Unit = {
    super.onInactive(ctx)
    val channel = ctx.channel()
    notifyListener(channel, Disassociated(AssociationHandle.Unknown))
    log.debug("Remote connection to [{}] was disconnected because of channel inactive.", channel.remoteAddress())
  }

  override def onMessage(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val byteBuf = msg.asInstanceOf[ByteBuf]
    val bytes: Array[Byte] = ByteBufUtil.getBytes(byteBuf)
    ReferenceCountUtil.safeRelease(msg)
    if (bytes.length > 0) {
      notifyListener(ctx.channel(), InboundPayload(ByteString(bytes)))
    }
  }

  override protected def onException(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    super.onException(ctx, cause)
    val channel = ctx.channel()
    notifyListener(channel, Disassociated(AssociationHandle.Unknown))
    log.warning("Remote connection to [{}] failed with {}", channel.remoteAddress(), cause)
    channel.close() // No graceful close here
  }

  private def notifyListener(channel: Channel, msg: HandleEvent): Unit = {
    val listener = channel.attr(LISTENER).get()
    listener.foreach(_.notify(msg))
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

  override def onActive(ctx: ChannelHandlerContext): Unit = {
    super.onActive(ctx)
    val channel = ctx.channel()
    initInbound(channel, channel.remoteAddress(), null)
  }
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[remote] class TcpClientHandler(_transport: NettyTransport, remoteAddress: Address, val log: LoggingAdapter)
    extends ClientHandler(_transport, remoteAddress)
    with TcpHandlers {

  override def onActive(ctx: ChannelHandlerContext): Unit = {
    super.onActive(ctx)
    val channel = ctx.channel()
    initOutbound(channel, channel.remoteAddress(), null)
  }
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
      val length = payload.length
      channel.writeAndFlush(channel.alloc().buffer(length).writeBytes(payload.asByteBuffer))
      true
    } else false

  override def disassociate(): Unit = NettyTransport.gracefulClose(channel)
}

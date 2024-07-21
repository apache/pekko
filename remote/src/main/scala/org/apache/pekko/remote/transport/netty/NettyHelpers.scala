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

import java.nio.channels.ClosedChannelException

import scala.annotation.nowarn
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.PekkoException
import pekko.util.unused

import io.netty.buffer.ByteBuf
import io.netty.channel.{ ChannelHandlerContext, SimpleChannelInboundHandler }

/**
 * INTERNAL API
 */
private[netty] trait NettyHelpers {

  protected def onConnect(@unused ctx: ChannelHandlerContext): Unit = ()

  protected def onDisconnect(@unused ctx: ChannelHandlerContext): Unit = ()

  protected def onOpen(@unused ctx: ChannelHandlerContext): Unit = ()

  protected def onMessage(@unused ctx: ChannelHandlerContext, @unused msg: ByteBuf): Unit = ()

  protected def onException(@unused ctx: ChannelHandlerContext, @unused e: Throwable): Unit = ()

  final protected def transformException(ctx: ChannelHandlerContext, ex: Throwable): Unit = {
    val cause = if (ex ne null) ex else new PekkoException("Unknown cause")
    cause match {
      case _: ClosedChannelException => // Ignore
      case null | NonFatal(_)        => onException(ctx, ex)
      case e: Throwable              => throw e // Rethrow fatals
    }
  }
}

/**
 * INTERNAL API
 */
private[netty] abstract class NettyChannelHandlerAdapter extends SimpleChannelInboundHandler[ByteBuf]
    with NettyHelpers {
  final override def channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf): Unit =
    onMessage(ctx, msg)

  @nowarn("msg=deprecated")
  final override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    transformException(ctx, cause)

  final override def channelActive(ctx: ChannelHandlerContext): Unit = {
    onOpen(ctx)
    onConnect(ctx)
  }

  final override def channelInactive(ctx: ChannelHandlerContext): Unit =
    onDisconnect(ctx)
}

/**
 * INTERNAL API
 */
private[netty] trait NettyServerHelpers extends NettyChannelHandlerAdapter

/**
 * INTERNAL API
 */
private[netty] trait NettyClientHelpers extends NettyChannelHandlerAdapter

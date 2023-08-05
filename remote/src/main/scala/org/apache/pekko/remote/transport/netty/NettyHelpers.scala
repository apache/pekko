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

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }

/**
 * INTERNAL API
 */
private[netty] trait NettyHelpers {

  protected def onActive(@unused ctx: ChannelHandlerContext): Unit = ()

  protected def onInactive(@unused ctx: ChannelHandlerContext): Unit = ()

  protected def onMessage(@unused ctx: ChannelHandlerContext, @unused msg: Any): Unit = ()

  protected def onException(@unused ctx: ChannelHandlerContext, @unused cause: Throwable): Unit = ()

  final protected def transformException(ctx: ChannelHandlerContext, exception: Throwable): Unit = {
    val cause = if (exception.getCause ne null) exception.getCause else new PekkoException("Unknown cause")
    cause match {
      case _: ClosedChannelException => // Ignore
      case null | NonFatal(_)        => onException(ctx, exception)
      case e: Throwable              => throw e // Rethrow fatals
    }
  }
}

/**
 * INTERNAL API
 */
private[netty] trait NettyServerHelpers extends ChannelInboundHandlerAdapter with NettyHelpers {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    super.channelRead(ctx, msg)
    onMessage(ctx, msg)
  }

  @nowarn("msg=deprecated")
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    transformException(ctx, cause)
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    super.channelActive(ctx)
    onActive(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    onInactive(ctx)
  }
}

/**
 * INTERNAL API
 */
private[netty] trait NettyClientHelpers extends ChannelInboundHandlerAdapter with NettyHelpers {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    super.channelRead(ctx, msg)
    onMessage(ctx, msg)
  }

  @nowarn("msg=deprecated")
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    transformException(ctx, cause)
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    super.channelActive(ctx)
    onActive(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    onInactive(ctx)
  }
}

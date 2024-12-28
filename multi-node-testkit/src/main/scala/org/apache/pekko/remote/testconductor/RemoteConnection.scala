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

package org.apache.pekko.remote.testconductor

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import scala.util.control.NonFatal

import io.netty.bootstrap.{ Bootstrap, ServerBootstrap }
import io.netty.buffer.{ ByteBuf, ByteBufUtil }
import io.netty.channel._
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.{ NioServerSocketChannel, NioSocketChannel }
import io.netty.handler.codec.{
  LengthFieldBasedFrameDecoder,
  LengthFieldPrepender,
  MessageToMessageDecoder,
  MessageToMessageEncoder
}

import org.apache.pekko
import pekko.protobufv3.internal.Message
import pekko.util.Helpers

/**
 * INTERNAL API.
 */
private[pekko] class ProtobufEncoder extends MessageToMessageEncoder[Message] {

  override def encode(ctx: ChannelHandlerContext, msg: Message, out: java.util.List[AnyRef]): Unit = {
    msg match {
      case message: Message =>
        val bytes = message.toByteArray
        out.add(ctx.alloc().buffer(bytes.length).writeBytes(bytes))
    }
  }
}

/**
 * INTERNAL API.
 */
private[pekko] class ProtobufDecoder(prototype: Message) extends MessageToMessageDecoder[ByteBuf] {

  override def decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: java.util.List[AnyRef]): Unit = {
    try {
      val bytes = ByteBufUtil.getBytes(msg)
      out.add(prototype.getParserForType.parseFrom(bytes))
    } catch {
      case NonFatal(e) => ctx.pipeline().fireExceptionCaught(e)
    } finally {
      msg.release()
    }
  }
}

/**
 * INTERNAL API.
 */
@Sharable
private[pekko] class TestConductorPipelineFactory(
    handler: ChannelInboundHandler) extends ChannelInitializer[SocketChannel] {

  override def initChannel(ch: SocketChannel): Unit = {
    val pipe = ch.pipeline()
    pipe.addLast("lengthFieldPrepender", new LengthFieldPrepender(4))
    pipe.addLast("lengthFieldDecoder", new LengthFieldBasedFrameDecoder(10000, 0, 4, 0, 4, false))
    pipe.addLast("protoEncoder", new ProtobufEncoder)
    pipe.addLast("protoDecoder", new ProtobufDecoder(TestConductorProtocol.Wrapper.getDefaultInstance))
    pipe.addLast("msgEncoder", new MsgEncoder)
    pipe.addLast("msgDecoder", new MsgDecoder)
    pipe.addLast("userHandler", handler)
  }
}

/**
 * INTERNAL API.
 */
private[pekko] sealed trait Role

/**
 * INTERNAL API.
 */
private[pekko] case object Client extends Role

/**
 * INTERNAL API.
 */
private[pekko] case object Server extends Role

/**
 * INTERNAL API.
 */
private[pekko] trait RemoteConnection {

  /**
   * The channel future associated with this connection.
   */
  def channelFuture: ChannelFuture

  /**
   * Shutdown the connection and release the resources.
   */
  def shutdown(): Unit
}

/**
 * INTERNAL API.
 */
private[pekko] object RemoteConnection {
  def apply(
      role: Role,
      sockaddr: InetSocketAddress,
      poolSize: Int,
      handler: ChannelInboundHandler): RemoteConnection = {
    role match {
      case Client =>
        val bootstrap = new Bootstrap()
        val eventLoopGroup = new NioEventLoopGroup(poolSize)
        val cf = bootstrap
          .group(eventLoopGroup)
          .channel(classOf[NioSocketChannel])
          .handler(new TestConductorPipelineFactory(handler))
          .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
          .option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
          .connect(sockaddr)
          .sync()

        new RemoteConnection {
          override def channelFuture: ChannelFuture = cf

          override def shutdown(): Unit = {
            try {
              channelFuture.channel().close().sync()
              eventLoopGroup.shutdownGracefully(0L, 0L, TimeUnit.SECONDS)
            } catch {
              case NonFatal(_) => // silence this one to not make tests look like they failed, it's not really critical
            }
          }
        }

      case Server =>
        val bootstrap = new ServerBootstrap()
        val parentEventLoopGroup = new NioEventLoopGroup(poolSize)
        val childEventLoopGroup = new NioEventLoopGroup(poolSize)
        val cf = bootstrap
          .group(parentEventLoopGroup, childEventLoopGroup)
          .channel(classOf[NioServerSocketChannel])
          .childHandler(new TestConductorPipelineFactory(handler))
          .option[java.lang.Boolean](ChannelOption.SO_REUSEADDR, !Helpers.isWindows)
          .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 2048)
          .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
          .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
          .bind(sockaddr)

        new RemoteConnection {
          override def channelFuture: ChannelFuture = cf

          override def shutdown(): Unit = {
            try {
              channelFuture.channel().close().sync()
              parentEventLoopGroup.shutdownGracefully(0L, 0L, TimeUnit.SECONDS)
              childEventLoopGroup.shutdownGracefully(0L, 0L, TimeUnit.SECONDS)
            } catch {
              case NonFatal(_) => // silence this one to not make tests look like they failed, it's not really critical
            }
          }
        }
    }
  }

  def getAddrString(channel: Channel): String = channel.remoteAddress() match {
    case i: InetSocketAddress => i.toString
    case _                    => "[unknown]"
  }
}

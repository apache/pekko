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

import scala.annotation.nowarn

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
import pekko.event.Logging
import pekko.protobufv3.internal.{ Message, MessageLite, MessageLiteOrBuilder }
import pekko.util.Helpers

/**
 * INTERNAL API.
 */
private[pekko] class ProtobufEncoder extends MessageToMessageEncoder[MessageLiteOrBuilder] {

  override def encode(ctx: ChannelHandlerContext, msg: MessageLiteOrBuilder, out: java.util.List[AnyRef]): Unit = {
    msg match {
      case messageLite: MessageLite =>
        val bytes = messageLite.toByteArray
        out.add(ctx.alloc().buffer(bytes.length).writeBytes(bytes))
      case messageBuilder: MessageLite.Builder =>
        val bytes = messageBuilder.build().toByteArray
        out.add(ctx.alloc().buffer(bytes.length).writeBytes(bytes))
      case _ => throw new IllegalArgumentException(s"Unsupported msg:$msg")
    }
  }
}

/**
 * INTERNAL API.
 */
private[pekko] class ProtobufDecoder(prototype: Message) extends MessageToMessageDecoder[ByteBuf] {

  override def decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: java.util.List[AnyRef]): Unit = {
    val bytes = ByteBufUtil.getBytes(msg)
    out.add(prototype.getParserForType.parseFrom(bytes))
  }
}

/**
 * INTERNAL API.
 */
@Sharable
private[pekko] class TestConductorPipelineFactory(
    handler: ChannelInboundHandler) extends ChannelInitializer[SocketChannel] {

  override def initChannel(ch: SocketChannel): Unit = {
    val encap = List(new LengthFieldPrepender(4), new LengthFieldBasedFrameDecoder(10000, 0, 4, 0, 4, false))
    val proto = List(new ProtobufEncoder, new ProtobufDecoder(TestConductorProtocol.Wrapper.getDefaultInstance))
    val msg = List(new MsgEncoder, new MsgDecoder)
    (encap ::: proto ::: msg ::: handler :: Nil).foldLeft(ch.pipeline()) { (pipe, handler) =>
      pipe.addLast(Logging.simpleName(handler.getClass), handler); pipe
    }
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
  def channel: Channel
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
        val clientChannel = bootstrap
          .group(eventLoopGroup)
          .channel(classOf[NioSocketChannel])
          .handler(new TestConductorPipelineFactory(handler))
          .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
          .option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
          .connect(sockaddr)
          .channel()
        new RemoteConnection {
          override def channel: Channel = clientChannel

          @nowarn("msg=deprecated")
          override def shutdown(): Unit = {
            clientChannel.close().sync()
            eventLoopGroup.shutdown()
          }
        }

      case Server =>
        val bootstrap = new ServerBootstrap()
        val parentEventLoopGroup = new NioEventLoopGroup(poolSize)
        val childEventLoopGroup = new NioEventLoopGroup(poolSize)
        val serverChannel = bootstrap
          .group(parentEventLoopGroup, childEventLoopGroup)
          .channel(classOf[NioServerSocketChannel])
          .childHandler(new TestConductorPipelineFactory(handler))
          .option[java.lang.Boolean](ChannelOption.SO_REUSEADDR, !Helpers.isWindows)
          .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 2048)
          .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
          .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
          .bind(sockaddr)
          .channel()
        new RemoteConnection {
          override def channel: Channel = serverChannel

          @nowarn("msg=deprecated")
          override def shutdown(): Unit = {
            serverChannel.close().sync()
            parentEventLoopGroup.shutdown()
            childEventLoopGroup.shutdown()
            parentEventLoopGroup.terminationFuture().sync()
            childEventLoopGroup.terminationFuture().sync()
          }
        }
    }
  }

  def getAddrString(channel: Channel): String = channel.remoteAddress() match {
    case i: InetSocketAddress => i.toString
    case _                    => "[unknown]"
  }
}

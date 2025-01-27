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

import java.net.{ InetAddress, InetSocketAddress, SocketAddress }
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.nowarn
import scala.concurrent.{ blocking, ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.{ NoStackTrace, NonFatal }

import com.typesafe.config.Config

import org.apache.pekko
import pekko.ConfigurationException
import pekko.OnlyCauseStackTrace
import pekko.actor.{ ActorSystem, Address, ExtendedActorSystem }
import pekko.dispatch.ThreadPoolConfig
import pekko.event.Logging
import pekko.remote.RARP
import pekko.remote.transport.{ AssociationHandle, Transport }
import pekko.remote.transport.AssociationHandle.HandleEventListener
import pekko.remote.transport.Transport._
import pekko.util.Helpers.Requiring
import pekko.util.{ Helpers, OptionVal }

import io.netty.bootstrap.{ Bootstrap => ClientBootstrap, ServerBootstrap }
import io.netty.buffer.{
  AdaptiveByteBufAllocator,
  ByteBufAllocator,
  PooledByteBufAllocator,
  Unpooled,
  UnpooledByteBufAllocator
}
import io.netty.channel.{
  Channel,
  ChannelFuture,
  ChannelHandlerContext,
  ChannelInitializer,
  ChannelOption,
  ChannelPipeline
}
import io.netty.channel.group.{ ChannelGroup, ChannelGroupFuture, ChannelMatchers, DefaultChannelGroup }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.{ NioServerSocketChannel, NioSocketChannel }
import io.netty.handler.codec.{ LengthFieldBasedFrameDecoder, LengthFieldPrepender }
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.handler.ssl.SslHandler
import io.netty.util.concurrent.GlobalEventExecutor

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
object NettyFutureBridge {
  def apply(nettyFuture: ChannelFuture): Future[Channel] = {
    val p = Promise[Channel]()
    nettyFuture.addListener((future: ChannelFuture) =>
      p.complete(
        Try(
          if (future.isSuccess) future.channel()
          else if (future.isCancelled) throw new CancellationException
          else throw future.cause())))
    p.future
  }

  private[transport] def apply[T](nettyFuture: io.netty.util.concurrent.Future[T]): Future[T] = {
    val p = Promise[T]()
    nettyFuture.addListener((future: io.netty.util.concurrent.Future[T]) =>
      p.complete(
        Try(
          if (future.isSuccess) future.get()
          else if (future.isCancelled) throw new CancellationException
          else throw future.cause())))
    p.future
  }

  def apply(nettyFuture: ChannelGroupFuture): Future[ChannelGroup] = {
    import pekko.util.ccompat.JavaConverters._
    val p = Promise[ChannelGroup]()
    nettyFuture.addListener((future: ChannelGroupFuture) =>
      p.complete(
        Try(
          if (future.isSuccess) future.group()
          else
            throw future.iterator.asScala
              .collectFirst {
                case f if f.isCancelled => new CancellationException
                case f if !f.isSuccess  => f.cause()
              }
              .getOrElse(new IllegalStateException(
                "Error reported in ChannelGroupFuture, but no error found in individual futures.")))))
    p.future
  }
}

@SerialVersionUID(1L)
@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
class NettyTransportException(msg: String, cause: Throwable)
    extends RuntimeException(msg, cause)
    with OnlyCauseStackTrace {
  def this(msg: String) = this(msg, null)
}

@SerialVersionUID(1L)
@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
class NettyTransportExceptionNoStack(msg: String, cause: Throwable)
    extends NettyTransportException(msg, cause)
    with NoStackTrace {
  def this(msg: String) = this(msg, null)
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
class NettyTransportSettings(config: Config) {

  import config._
  import pekko.util.Helpers.ConfigOps

  val EnableSsl: Boolean = getBoolean("enable-ssl")

  val SSLEngineProviderClassName: String = if (EnableSsl) getString("ssl-engine-provider") else ""

  val UseDispatcherForIo: Option[String] = getString("use-dispatcher-for-io") match {
    case "" | null  => None
    case dispatcher => Some(dispatcher)
  }

  private[this] def optionSize(s: String): Option[Int] = getBytes(s).toInt match {
    case 0          => None
    case x if x < 0 => throw new ConfigurationException(s"Setting '$s' must be 0 or positive (and fit in an Int)")
    case other      => Some(other)
  }

  val ConnectionTimeout: FiniteDuration = config.getMillisDuration("connection-timeout")

  val WriteBufferHighWaterMark: Option[Int] = optionSize("write-buffer-high-water-mark")

  val WriteBufferLowWaterMark: Option[Int] = optionSize("write-buffer-low-water-mark")

  val SendBufferSize: Option[Int] = optionSize("send-buffer-size")

  val ReceiveBufferSize: Option[Int] = optionSize("receive-buffer-size")

  val MaxFrameSize: Int = getBytes("maximum-frame-size").toInt
    .requiring(_ >= 32000, s"Setting 'maximum-frame-size' must be at least 32000 bytes")

  val Backlog: Int = getInt("backlog")

  val TcpNodelay: Boolean = getBoolean("tcp-nodelay")

  val TcpKeepalive: Boolean = getBoolean("tcp-keepalive")

  val TcpReuseAddr: Boolean = getString("tcp-reuse-addr") match {
    case "off-for-windows" => !Helpers.isWindows
    case _                 => getBoolean("tcp-reuse-addr")
  }

  val ByteBufAllocator: ByteBufAllocator = NettyTransport.deriveByteBufAllocator(getString("bytebuf-allocator-type"))

  val Hostname: String = getString("hostname") match {
    case ""    => InetAddress.getLocalHost.getHostAddress
    case value => value
  }

  val BindHostname: String = getString("bind-hostname") match {
    case ""    => Hostname
    case value => value
  }

  @deprecated("WARNING: This should only be used by professionals.", "Akka 2.0")
  val PortSelector: Int = getInt("port")

  @deprecated("WARNING: This should only be used by professionals.", "Akka 2.4")
  @nowarn("msg=deprecated")
  val BindPortSelector: Int = getString("bind-port") match {
    case ""    => PortSelector
    case value => value.toInt
  }

  val SslSettings: Option[SSLSettings] = if (EnableSsl) Some(new SSLSettings(config.getConfig("security"))) else None

  val ServerSocketWorkerPoolSize: Int = computeWPS(config.getConfig("server-socket-worker-pool"))

  val ClientSocketWorkerPoolSize: Int = computeWPS(config.getConfig("client-socket-worker-pool"))

  private def computeWPS(config: Config): Int =
    ThreadPoolConfig.scaledPoolSize(
      config.getInt("pool-size-min"),
      config.getDouble("pool-size-factor"),
      config.getInt("pool-size-max"))
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[netty] trait CommonHandlers extends NettyHelpers {
  protected val transport: NettyTransport

  override protected def onOpen(ctx: ChannelHandlerContext): Unit = {
    transport.channelGroup.add(ctx.channel())
  }

  protected def createHandle(channel: Channel, localAddress: Address, remoteAddress: Address): AssociationHandle

  protected def registerListener(
      channel: Channel,
      listener: HandleEventListener,
      remoteSocketAddress: InetSocketAddress): Unit

  final protected def init(
      channel: Channel,
      remoteSocketAddress: SocketAddress,
      remoteAddress: Address)(op: AssociationHandle => Any): Unit = {
    import transport._
    NettyTransport.addressFromSocketAddress(
      channel.localAddress(),
      schemeIdentifier,
      system.name,
      Some(settings.Hostname),
      None) match {
      case Some(localAddress) =>
        val handle = createHandle(channel, localAddress, remoteAddress)
        handle.readHandlerPromise.future.foreach { listener =>
          registerListener(channel, listener, remoteSocketAddress.asInstanceOf[InetSocketAddress])
          channel.config().setAutoRead(true)
        }
        op(handle)

      case _ => NettyTransport.gracefulClose(channel)
    }
  }
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[netty] abstract class ServerHandler(
    protected final val transport: NettyTransport,
    private final val associationListenerFuture: Future[AssociationEventListener])
    extends NettyServerHelpers
    with CommonHandlers {

  import transport.executionContext

  final protected def initInbound(channel: Channel, remoteSocketAddress: SocketAddress): Unit = {
    channel.config.setAutoRead(false)
    associationListenerFuture.foreach { listener =>
      val remoteAddress = NettyTransport
        .addressFromSocketAddress(
          remoteSocketAddress,
          transport.schemeIdentifier,
          transport.system.name,
          hostName = None,
          port = None)
        .getOrElse(throw new NettyTransportException(
          s"Unknown inbound remote address type [${remoteSocketAddress.getClass.getName}]"))
      init(channel, remoteSocketAddress, remoteAddress) { a =>
        listener.notify(InboundAssociation(a))
      }
    }
  }

}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[netty] abstract class ClientHandler(protected final val transport: NettyTransport, remoteAddress: Address)
    extends NettyClientHelpers
    with CommonHandlers {
  final protected val statusPromise = Promise[AssociationHandle]()
  def statusFuture: Future[AssociationHandle] = statusPromise.future

  final protected def initOutbound(channel: Channel, remoteSocketAddress: SocketAddress): Unit = {
    init(channel, remoteSocketAddress, remoteAddress)(statusPromise.success)
  }

}

/**
 * INTERNAL API
 */
private[transport] object NettyTransport {
  // 4 bytes will be used to represent the frame length. Used by netty LengthFieldPrepender downstream handler.
  val FrameLengthFieldLength = 4
  def gracefulClose(channel: Channel)(implicit ec: ExecutionContext): Unit = {
    @nowarn("msg=deprecated")
    def always(c: ChannelFuture): Future[Channel] = NettyFutureBridge(c).recover { case _ => c.channel() }
    for {
      _ <- always { channel.writeAndFlush(Unpooled.EMPTY_BUFFER) } // Force flush by waiting on a final dummy write
      _ <- always { channel.disconnect() }
    } channel.close()
  }

  val uniqueIdCounter = new AtomicInteger(0)

  def addressFromSocketAddress(
      addr: SocketAddress,
      schemeIdentifier: String,
      systemName: String,
      hostName: Option[String],
      port: Option[Int]): Option[Address] = addr match {
    case sa: InetSocketAddress =>
      Some(Address(schemeIdentifier, systemName, hostName.getOrElse(sa.getHostString), port.getOrElse(sa.getPort)))
    case _ => None
  }

  // Need to do like this for binary compatibility reasons
  def addressFromSocketAddress(
      addr: SocketAddress,
      schemeIdentifier: String,
      systemName: String,
      hostName: Option[String]): Option[Address] =
    addressFromSocketAddress(addr, schemeIdentifier, systemName, hostName, port = None)

  def deriveByteBufAllocator(allocatorType: String): ByteBufAllocator = allocatorType match {
    case "pooled"        => PooledByteBufAllocator.DEFAULT
    case "unpooled"      => UnpooledByteBufAllocator.DEFAULT
    case "unpooled-heap" => new UnpooledByteBufAllocator(false)
    case "adaptive"      => new AdaptiveByteBufAllocator()
    case "adaptive-heap" => new AdaptiveByteBufAllocator(false)
    case other => throw new IllegalArgumentException(
        "Unknown 'bytebuf-allocator-type' [" + other + "]," +
        " supported values are 'pooled', 'unpooled', 'unpooled-heap', 'adaptive', 'adaptive-heap'.")
  }
}

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
class NettyTransport(val settings: NettyTransportSettings, val system: ExtendedActorSystem) extends Transport {

  def this(system: ExtendedActorSystem, conf: Config) = this(new NettyTransportSettings(conf), system)

  import NettyTransport._
  import settings._

  implicit val executionContext: ExecutionContext =
    settings.UseDispatcherForIo
      .orElse(RARP(system).provider.remoteSettings.Dispatcher match {
        case ""             => None
        case dispatcherName => Some(dispatcherName)
      })
      .map(system.dispatchers.lookup)
      .getOrElse(system.dispatcher)

  override val schemeIdentifier: String = (if (EnableSsl) "ssl." else "") + "tcp"
  override def maximumPayloadBytes: Int = settings.MaxFrameSize

  @volatile private var boundTo: Address = _
  @volatile private var serverChannel: Channel = _

  private val log = Logging.withMarker(system, classOf[NettyTransport])

  private def createEventLoopGroup(nThreadCount: Int): NioEventLoopGroup =
    UseDispatcherForIo.map(system.dispatchers.lookup)
      .map(executor => new NioEventLoopGroup(0, executor))
      .getOrElse(new NioEventLoopGroup(nThreadCount, system.threadFactory))

  /*
   * Be aware, that the close() method of DefaultChannelGroup is racy, because it uses an iterator over a ConcurrentHashMap.
   * In the old remoting this was handled by using a custom subclass, guarding the close() method with a write-lock.
   * The usage of this class is safe in the new remoting, as close() is called after unbind() is finished, and no
   * outbound connections are initiated in the shutdown phase.
   */
  val channelGroup = new DefaultChannelGroup(
    "pekko-netty-transport-driver-channelgroup-" +
    uniqueIdCounter.getAndIncrement,
    GlobalEventExecutor.INSTANCE)

  private val clientEventLoopGroup = createEventLoopGroup(ClientSocketWorkerPoolSize + 1)

  private val serverEventLoopParentGroup = createEventLoopGroup(0)

  private val serverEventLoopChildGroup = createEventLoopGroup(ServerSocketWorkerPoolSize)

  private def newPipeline(channel: Channel): ChannelPipeline = {
    val pipeline = channel.pipeline()
    pipeline.addFirst("FlushConsolidationHandler",
      new FlushConsolidationHandler(FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true))
    pipeline.addLast(
      "FrameDecoder",
      new LengthFieldBasedFrameDecoder(
        maximumPayloadBytes,
        0,
        FrameLengthFieldLength,
        0,
        FrameLengthFieldLength, // Strip the header
        true))
    pipeline.addLast("FrameEncoder", new LengthFieldPrepender(FrameLengthFieldLength))

    pipeline
  }

  private val associationListenerPromise: Promise[AssociationEventListener] = Promise()

  private val sslEngineProvider: OptionVal[SSLEngineProvider] =
    if (settings.EnableSsl) {
      OptionVal.Some(system.dynamicAccess
        .createInstanceFor[SSLEngineProvider](settings.SSLEngineProviderClassName, List((classOf[ActorSystem], system)))
        .recover {
          case e =>
            throw new ConfigurationException(
              s"Could not create SSLEngineProvider [${settings.SSLEngineProviderClassName}]",
              e)
        }
        .get)
    } else OptionVal.None

  private def sslHandler(isClient: Boolean): SslHandler = {
    sslEngineProvider match {
      case OptionVal.Some(sslProvider) =>
        NettySSLSupport(sslProvider, isClient)
      case _ =>
        throw new IllegalStateException("Expected enable-ssl=on")
    }

  }

  private val serverPipelineInitializer: ChannelInitializer[SocketChannel] = (ch: SocketChannel) => {
    val pipeline = newPipeline(ch)
    if (EnableSsl) pipeline.addFirst("SslHandler", sslHandler(isClient = false))
    val handler = new TcpServerHandler(NettyTransport.this, associationListenerPromise.future, log)
    pipeline.addLast("ServerHandler", handler)
  }

  private def clientPipelineInitializer(remoteAddress: Address): ChannelInitializer[SocketChannel] =
    (ch: SocketChannel) => {
      val pipeline = newPipeline(ch)
      if (EnableSsl) pipeline.addFirst("SslHandler", sslHandler(isClient = true))
      val handler = new TcpClientHandler(NettyTransport.this, remoteAddress, log)
      pipeline.addLast("clienthandler", handler)
    }

  private val inboundBootstrap: ServerBootstrap = {
    val bootstrap = new ServerBootstrap()
    bootstrap.group(serverEventLoopParentGroup, serverEventLoopChildGroup)

    bootstrap.channel(classOf[NioServerSocketChannel])
    bootstrap.childHandler(serverPipelineInitializer)
    // DO NOT AUTO READ
    bootstrap.option[java.lang.Boolean](ChannelOption.AUTO_READ, false)

    bootstrap.option[java.lang.Integer](ChannelOption.SO_BACKLOG, settings.Backlog)
    bootstrap.option[java.lang.Boolean](ChannelOption.SO_REUSEADDR, settings.TcpReuseAddr)

    // DO NOT AUTO READ
    bootstrap.childOption[java.lang.Boolean](ChannelOption.AUTO_READ, false)
    bootstrap.childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, settings.TcpNodelay)
    bootstrap.childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, settings.TcpKeepalive)

    // Use the same allocator for inbound and outbound buffers
    bootstrap.option(ChannelOption.ALLOCATOR, settings.ByteBufAllocator)
    bootstrap.childOption(ChannelOption.ALLOCATOR, settings.ByteBufAllocator)

    settings.ReceiveBufferSize.foreach(sz => bootstrap.childOption[java.lang.Integer](ChannelOption.SO_RCVBUF, sz))
    settings.SendBufferSize.foreach(sz => bootstrap.childOption[java.lang.Integer](ChannelOption.SO_SNDBUF, sz))
    settings.WriteBufferHighWaterMark.filter(_ > 0).foreach(sz =>
      bootstrap.childOption[java.lang.Integer](ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, sz))
    settings.WriteBufferLowWaterMark.filter(_ > 0).foreach(sz =>
      bootstrap.childOption[java.lang.Integer](ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, sz))
    bootstrap
  }

  private def outboundBootstrap(remoteAddress: Address): ClientBootstrap = {
    val bootstrap = new ClientBootstrap()
    bootstrap.group(clientEventLoopGroup)
    bootstrap.handler(clientPipelineInitializer(remoteAddress))
    bootstrap.channel(classOf[NioSocketChannel])
    // DO NOT AUTO READ
    bootstrap.option[java.lang.Boolean](ChannelOption.AUTO_READ, false)

    bootstrap.option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, settings.ConnectionTimeout.toMillis.toInt)
    bootstrap.option[java.lang.Boolean](ChannelOption.TCP_NODELAY, settings.TcpNodelay)
    bootstrap.option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, settings.TcpKeepalive)

    settings.ReceiveBufferSize.foreach(sz => bootstrap.option[java.lang.Integer](ChannelOption.SO_RCVBUF, sz))
    settings.SendBufferSize.foreach(sz => bootstrap.option[java.lang.Integer](ChannelOption.SO_SNDBUF, sz))

    settings.WriteBufferHighWaterMark.filter(_ > 0).foreach(sz =>
      bootstrap.option[java.lang.Integer](ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, sz))
    settings.WriteBufferLowWaterMark.filter(_ > 0).foreach(sz =>
      bootstrap.option[java.lang.Integer](ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, sz))
    //
    bootstrap
  }

  override def isResponsibleFor(address: Address): Boolean = true // TODO: Add configurable subnet filtering

  // TODO: This should be factored out to an async (or thread-isolated) name lookup service #2960
  // Keep this for binary compatibility reasons
  def addressToSocketAddress(addr: Address): Future[InetSocketAddress] = addr match {
    case Address(_, _, Some(host), Some(port)) =>
      Future {
        blocking {
          new InetSocketAddress(InetAddress.getByName(host), port)
        }
      }
    case _ =>
      Future.failed(new IllegalArgumentException(s"Address [$addr] must contain both host and port information."))
  }

  override def listen: Future[(Address, Promise[AssociationEventListener])] = {
    @nowarn("msg=deprecated")
    val bindPort = settings.BindPortSelector
    Future.fromTry(Try {
      try {
        val newServerChannel = inboundBootstrap.bind(settings.BindHostname, bindPort).sync().channel()
        // Block reads until a handler actor is registered
        newServerChannel.config().setAutoRead(false)
        channelGroup.add(newServerChannel)

        serverChannel = newServerChannel

        @nowarn("msg=deprecated")
        val port = if (settings.PortSelector == 0) None else Some(settings.PortSelector)

        addressFromSocketAddress(
          newServerChannel.localAddress(),
          schemeIdentifier,
          system.name,
          Some(settings.Hostname),
          port) match {
          case Some(address) =>
            addressFromSocketAddress(newServerChannel.localAddress(), schemeIdentifier, system.name, None,
              None) match {
              case Some(address) => boundTo = address
              case None =>
                throw new NettyTransportException(
                  s"Unknown local address type [${newServerChannel.localAddress().getClass.getName}]")
            }
            associationListenerPromise.future.foreach { _ =>
              newServerChannel.config().setAutoRead(true)
            }
            (address, associationListenerPromise)
          case None =>
            throw new NettyTransportException(
              s"Unknown local address type [${newServerChannel.localAddress().getClass.getName}]")
        }
      } catch {
        case NonFatal(e) =>
          log.error("failed to bind to host:{} port:{}, shutting down Netty transport", settings.BindHostname, bindPort)
          try {
            shutdown()
          } catch {
            case NonFatal(_) =>
          } // ignore possible exception during shutdown
          throw e;
      }
    })
  }

  // Need to do like this for binary compatibility reasons
  private[pekko] def boundAddress = boundTo

  private def extractHostAndPort(addr: Address): (String, Int) = addr match {
    case Address(_, _, Some(host), Some(port)) => (host, port)
    case _                                     => throw new IllegalArgumentException(s"Address [$addr] must contain both host and port information.")
  }

  override def associate(remoteAddress: Address): Future[AssociationHandle] = {
    if (!serverChannel.isActive) Future.failed(new NettyTransportException("Transport is not bound"))
    else {
      val bootstrap: ClientBootstrap = outboundBootstrap(remoteAddress)

      (for {
        (host, port) <- Future.fromTry(Try(extractHostAndPort(remoteAddress)))
        channel <- NettyFutureBridge(bootstrap.connect(host, port))
        readyChannel <- if (EnableSsl) {
          NettyFutureBridge(channel.pipeline().get(classOf[SslHandler]).handshakeFuture())
        } else Future.successful(channel)
        handle <- readyChannel.pipeline().get(classOf[ClientHandler]).statusFuture
      } yield handle).recover {
        case _: CancellationException => throw new NettyTransportExceptionNoStack("Connection was cancelled")
        case NonFatal(t) =>
          val msg =
            if (t.getCause == null)
              t.getMessage
            else if (t.getCause.getCause == null)
              s"${t.getMessage}, caused by: ${t.getCause}"
            else
              s"${t.getMessage}, caused by: ${t.getCause}, caused by: ${t.getCause.getCause}"
          throw new NettyTransportExceptionNoStack(s"${t.getClass.getName}: $msg", t.getCause)
      }
    }
  }

  override def shutdown(): Future[Boolean] = {
    def always(c: ChannelGroupFuture): Future[Boolean] = NettyFutureBridge(c).map(_ => true).recover { case _ => false }
    for {
      // Force flush by trying to write an empty buffer and wait for success
      unbindStatus <- always(channelGroup.close(ChannelMatchers.isServerChannel))
      lastWriteStatus <- always(channelGroup.writeAndFlush(Unpooled.EMPTY_BUFFER))
      disconnectStatus <- always(channelGroup.disconnect())
      closeStatus <- always(channelGroup.close())
    } yield {
      // Release the selectors, but don't try to kill the dispatcher
      clientEventLoopGroup.shutdownGracefully()
      serverEventLoopParentGroup.shutdownGracefully()
      serverEventLoopChildGroup.shutdownGracefully()
      lastWriteStatus && unbindStatus && disconnectStatus && closeStatus
    }

  }

}

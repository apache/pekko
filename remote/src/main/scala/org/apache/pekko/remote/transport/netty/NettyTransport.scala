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

import org.apache.pekko.{ ConfigurationException, OnlyCauseStackTrace }
import org.apache.pekko.actor.{ ActorSystem, Address, ExtendedActorSystem }
import org.apache.pekko.dispatch.ThreadPoolConfig
import org.apache.pekko.event.Logging
import org.apache.pekko.remote.RARP
import org.apache.pekko.remote.transport.{ AssociationHandle, Transport }
import org.apache.pekko.remote.transport.AssociationHandle.HandleEventListener
import org.apache.pekko.remote.transport.Transport._
import org.apache.pekko.util.{ Helpers, OptionVal }
import org.apache.pekko.util.Helpers.Requiring

import io.netty.bootstrap.{ Bootstrap, ServerBootstrap }
import io.netty.buffer.{ ByteBuf, Unpooled }
import io.netty.channel._
import io.netty.channel.group.{
  ChannelGroup,
  ChannelGroupFuture,
  ChannelGroupFutureListener,
  ChannelMatchers,
  DefaultChannelGroup
}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.{ NioServerSocketChannel, NioSocketChannel }
import io.netty.handler.codec.{ LengthFieldBasedFrameDecoder, LengthFieldPrepender }
import io.netty.handler.ssl.SslHandler

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
object NettyFutureBridge {
  def apply(nettyFuture: ChannelFuture): Future[Channel] = {
    val p = Promise[Channel]()
    nettyFuture.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture): Unit =
        p.complete(
          Try(
            if (future.isSuccess) future.channel()
            else if (future.isCancelled) throw new CancellationException
            else throw future.cause()))
    })
    p.future
  }

  def apply(nettyFuture: ChannelGroupFuture): Future[ChannelGroup] = {
    import pekko.util.ccompat.JavaConverters._
    val p = Promise[ChannelGroup]()
    nettyFuture.addListener(new ChannelGroupFutureListener {
      def operationComplete(future: ChannelGroupFuture): Unit =
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
                  "Error reported in ChannelGroupFuture, but no error found in individual futures."))))
    })
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

  val ServerSocketWorkerPoolSize: Int = computeWPS(config.getConfig("server-socket-worker-pool"))

  val ClientSocketWorkerPoolSize: Int = computeWPS(config.getConfig("client-socket-worker-pool"))

  private def computeWPS(config: Config): Int =
    ThreadPoolConfig.scaledPoolSize(
      config.getInt("pool-size-min"),
      config.getDouble("pool-size-factor"),
      config.getInt("pool-size-max"))

  // Check Netty version >= 4.1.94
  {
    val nettyVersions = io.netty.util.Version.identify()
    val nettyVersion = nettyVersions.values().stream().filter(_.artifactId() == "netty-transport")
      .findFirst()
      .map(_.artifactVersion())
      .orElseThrow(() => throw new IllegalArgumentException("Can not read netty-transport's version."))

    def throwInvalidNettyVersion(): Nothing = {
      throw new IllegalArgumentException(
        "pekko-remote with the Netty transport requires Netty version 4.1.94 or " +
        s"later. Version [$nettyVersion] is on the class path. Issue https://github.com/netty/netty/pull/4739 " +
        "may cause messages to not be delivered.")
    }

    try {
      val segments: Array[String] = nettyVersion.split("[.-]")
      if (segments.length < 3 || segments(0).toInt != 4 || segments(1).toInt != 1 || segments(2).toInt < 94)
        throwInvalidNettyVersion()
    } catch {
      case _: NumberFormatException =>
        throwInvalidNettyVersion()
    }
  }

}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[netty] trait CommonHandlers extends NettyHelpers {
  protected val transport: NettyTransport

  override protected def onActive(ctx: ChannelHandlerContext): Unit = {
    transport.channelGroup.add(ctx.channel())
  }

  protected def createHandle(channel: Channel, localAddress: Address, remoteAddress: Address): AssociationHandle

  protected def registerListener(
      channel: Channel,
      listener: HandleEventListener,
      msg: ByteBuf,
      remoteSocketAddress: InetSocketAddress): Unit

  final protected def init(
      channel: Channel,
      remoteSocketAddress: SocketAddress,
      remoteAddress: Address,
      msg: ByteBuf)(op: AssociationHandle => Any): Unit = {
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
          // TODO use channel attr
          registerListener(channel, listener, msg, remoteSocketAddress.asInstanceOf[InetSocketAddress])
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

  final protected def initInbound(channel: Channel, remoteSocketAddress: SocketAddress, msg: ByteBuf): Unit = {
    channel.config().setAutoRead(false)
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
      init(channel, remoteSocketAddress, remoteAddress, msg) { a =>
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
  def statusFuture = statusPromise.future

  final protected def initOutbound(channel: Channel, remoteSocketAddress: SocketAddress, msg: ByteBuf): Unit = {
    init(channel, remoteSocketAddress, remoteAddress, msg)(statusPromise.success)
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
    def always(c: ChannelFuture) = NettyFutureBridge(c).recover { case _ => c.channel() }

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

  private val clientEventLoopGroup = new NioEventLoopGroup()

  private val serverParentEventLoopGroup = new NioEventLoopGroup()

  private val serverChildEventLoopGroup = new NioEventLoopGroup()

  /*
   * Be aware, that the close() method of DefaultChannelGroup is racy, because it uses an iterator over a ConcurrentHashMap.
   * In the old remoting this was handled by using a custom subclass, guarding the close() method with a write-lock.
   * The usage of this class is safe in the new remoting, as close() is called after unbind() is finished, and no
   * outbound connections are initiated in the shutdown phase.
   */
  val channelGroup = new DefaultChannelGroup(
    "pekko-netty-transport-driver-channelgroup-" + uniqueIdCounter.getAndIncrement,
    serverChildEventLoopGroup.next())

  private def setupPipeline(pipeline: ChannelPipeline): ChannelPipeline = {
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
        val handler = NettySSLSupport(sslProvider, isClient)
        handler
      case _ =>
        throw new IllegalStateException("Expected enable-ssl=on")
    }

  }

  private val serverChildChannelInitializer: ChannelInitializer[SocketChannel] = new ChannelInitializer[SocketChannel] {
    override def initChannel(ch: SocketChannel): Unit = {
      val pipeline = ch.pipeline()
      setupPipeline(pipeline)
      if (EnableSsl) pipeline.addFirst("SslHandler", sslHandler(isClient = false))
      val handler = new TcpServerHandler(NettyTransport.this, associationListenerPromise.future, log)
      pipeline.addLast("ServerHandler", handler)
    }
  }

  private def clientChannelInitializer(remoteAddress: Address): ChannelInitializer[SocketChannel] =
    new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel): Unit = {
        val pipeline = ch.pipeline()
        setupPipeline(pipeline)
        if (EnableSsl) pipeline.addFirst("SslHandler", sslHandler(isClient = true))
        val handler = new TcpClientHandler(NettyTransport.this, remoteAddress, log)
        pipeline.addLast("clientHandler", handler)
      }
    }

  private def setupBootstrap(bootstrap: Bootstrap, channelInitializer: ChannelInitializer[SocketChannel]): Bootstrap = {
    bootstrap.handler(channelInitializer)
    bootstrap.group(clientEventLoopGroup)
    bootstrap.channel(classOf[NioSocketChannel])
    bootstrap.option[java.lang.Boolean](ChannelOption.TCP_NODELAY, settings.TcpNodelay)
    bootstrap.option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, settings.TcpKeepalive)
    settings.ReceiveBufferSize.foreach(sz => bootstrap.option[java.lang.Integer](ChannelOption.SO_RCVBUF, sz))
    settings.SendBufferSize.foreach(sz => bootstrap.option[java.lang.Integer](ChannelOption.SO_SNDBUF, sz))
    settings.WriteBufferHighWaterMark.foreach(sz =>
      bootstrap.option[java.lang.Integer](ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, sz))
    settings.WriteBufferLowWaterMark.foreach(sz =>
      bootstrap.option[java.lang.Integer](ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, sz))
    bootstrap
  }

  private def setupBootstrap(
      bootstrap: ServerBootstrap, channelInitializer: ChannelInitializer[SocketChannel]): ServerBootstrap = {
    bootstrap.childHandler(channelInitializer)
    bootstrap.group(serverParentEventLoopGroup, serverChildEventLoopGroup)
    bootstrap.channel(classOf[NioServerSocketChannel])
    bootstrap.option[java.lang.Integer](ChannelOption.SO_BACKLOG, settings.Backlog)
    bootstrap.option[java.lang.Boolean](ChannelOption.SO_REUSEADDR, settings.TcpReuseAddr)
    bootstrap.childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, settings.TcpNodelay)
    bootstrap.childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, settings.TcpKeepalive)
    settings.ReceiveBufferSize.foreach(sz => bootstrap.childOption[java.lang.Integer](ChannelOption.SO_RCVBUF, sz))
    settings.SendBufferSize.foreach(sz => bootstrap.childOption[java.lang.Integer](ChannelOption.SO_SNDBUF, sz))
    settings.WriteBufferHighWaterMark.foreach(sz =>
      bootstrap.childOption[java.lang.Integer](ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, sz))
    settings.WriteBufferLowWaterMark.foreach(sz =>
      bootstrap.childOption[java.lang.Integer](ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, sz))
    bootstrap
  }

  private val inboundBootstrap: ServerBootstrap = {
    setupBootstrap(new ServerBootstrap(), serverChildChannelInitializer)
  }

  private def outboundBootstrap(remoteAddress: Address): Bootstrap = {
    val bootstrap = setupBootstrap(new Bootstrap(), clientChannelInitializer(remoteAddress))
    bootstrap.option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, settings.ConnectionTimeout.toMillis.toInt)
    bootstrap
  }

  override def isResponsibleFor(address: Address): Boolean = true // TODO: Add configurable subnet filtering

  // TODO: This should be factored out to an async (or thread-isolated) name lookup service #2960
  def addressToSocketAddress(addr: Address): Future[InetSocketAddress] = addr match {
    case Address(_, _, Some(host), Some(port)) =>
      Future { blocking { new InetSocketAddress(InetAddress.getByName(host), port) } }
    case _ => Future.failed(new IllegalArgumentException(s"Address [$addr] does not contain host or port information."))
  }

  override def listen: Future[(Address, Promise[AssociationEventListener])] = {
    @nowarn("msg=deprecated")
    val bindPort = settings.BindPortSelector

    for {
      address <- addressToSocketAddress(Address("", "", settings.BindHostname, bindPort))
    } yield {
      try {
        val newServerChannel = inboundBootstrap.bind(address).sync().channel()

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
            addressFromSocketAddress(newServerChannel.localAddress, schemeIdentifier, system.name, None,
              None) match {
              case Some(address) => boundTo = address
              case None =>
                throw new NettyTransportException(
                  s"Unknown local address type [${newServerChannel.localAddress.getClass.getName}]")
            }
            associationListenerPromise.future.foreach { _ =>
              newServerChannel.config().setAutoRead(true)
            }
            (address, associationListenerPromise)
          case None =>
            throw new NettyTransportException(
              s"Unknown local address type [${newServerChannel.localAddress.getClass.getName}]")
        }
      } catch {
        case NonFatal(e) => {
          log.error("failed to bind to {}, shutting down Netty transport", address)
          try {
            shutdown()
          } catch { case NonFatal(_) => } // ignore possible exception during shutdown
          throw e
        }
      }
    }
  }

  // Need to do like this for binary compatibility reasons
  private[pekko] def boundAddress = boundTo

  override def associate(remoteAddress: Address): Future[AssociationHandle] = {
    if (!serverChannel.isActive) Future.failed(new NettyTransportException("Transport is not bound"))
    else {
      val bootstrap: Bootstrap = outboundBootstrap(remoteAddress)

      (for {
        socketAddress <- addressToSocketAddress(remoteAddress)
        readyChannel <- NettyFutureBridge(bootstrap.connect(socketAddress)).map { channel =>
          if (EnableSsl)
            blocking {
              channel.pipeline().get(classOf[SslHandler]).renegotiate().awaitUninterruptibly()
            }
          channel.config().setAutoRead(false)
          channel
        }
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
      unbindStatus <- always { channelGroup.close(ChannelMatchers.isServerChannel) }
      lastWriteStatus <- always(channelGroup.writeAndFlush(Unpooled.EMPTY_BUFFER))
      disconnectStatus <- always(channelGroup.disconnect())
      closeStatus <- always(channelGroup.close())
    } yield {
      serverParentEventLoopGroup.shutdownGracefully()
      serverChildEventLoopGroup.shutdownGracefully()
      clientEventLoopGroup.shutdownGracefully()
      lastWriteStatus && unbindStatus && disconnectStatus && closeStatus
    }

  }

}

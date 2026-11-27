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

package org.apache.pekko.io

import java.net.{ ConnectException, InetSocketAddress }
import java.nio.channels.{ SelectionKey, SocketChannel }

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.control.{ NoStackTrace, NonFatal }

import org.apache.pekko
import pekko.Done
import pekko.actor.Status.Failure
import pekko.actor.{ ActorRef, ReceiveTimeout, Timers }
import pekko.annotation.InternalApi
import pekko.io.SelectionHandler._
import pekko.io.Tcp._
import pekko.io.TcpConnection.CloseInformation
import pekko.io.dns.DnsProtocol

/**
 * An actor handling the connection state machine for an outgoing connection
 * to be established.
 *
 * INTERNAL API
 */
private[io] class TcpOutgoingConnection(
    _tcp: TcpExt,
    channelRegistry: ChannelRegistry,
    commander: ActorRef,
    connect: Connect,
    transportTermination: Option[Promise[Done]])
    extends TcpConnection(
      _tcp,
      TcpOutgoingConnection.openChannel(transportTermination),
      connect.pullMode)
    with Timers {

  def this(tcp: TcpExt, channelRegistry: ChannelRegistry, commander: ActorRef, connect: Connect) =
    this(tcp, channelRegistry, commander, connect, None)

  import TcpOutgoingConnection._
  import connect._
  import context._

  signDeathPact(commander)

  reportConnectFailure {
    options.foreach(_.beforeConnect(channel.socket))
    localAddress.foreach(channel.socket.bind)
    channelRegistry.register(channel, 0)
    timeout.foreach(context.setReceiveTimeout) // Initiate connection timeout if supplied
  }

  private def stop(cause: Throwable): Unit =
    stopWith(CloseInformation(Set(commander), CommandFailed(connect).withCause(cause)), shouldAbort = true)

  private def reportConnectFailure(thunk: => Unit): Unit = {
    try {
      thunk
    } catch {
      case NonFatal(e) =>
        log.debug("Could not establish connection to [{}] due to {}", remoteAddress, e)
        stop(e)
    }
  }

  def receive: Receive = {
    case registration: ChannelRegistration =>
      setRegistration(registration)
      reportConnectFailure {
        if (remoteAddress.isUnresolved) {
          log.debug("Resolving {} before connecting", remoteAddress.getHostName)
          Dns.resolve(DnsProtocol.Resolve(remoteAddress.getHostName), system, self) match {
            case None =>
              context.become(resolving(registration))
            case Some(resolved) =>
              register(new InetSocketAddress(resolved.address(), remoteAddress.getPort), registration)
          }
        } else {
          register(remoteAddress, registration)
        }
      }
    case _: ReceiveTimeout =>
      connectionTimeout()
  }

  def resolving(registration: ChannelRegistration): Receive = {
    case resolved: DnsProtocol.Resolved =>
      reportConnectFailure {
        register(new InetSocketAddress(resolved.address(), remoteAddress.getPort), registration)
      }
    case _: ReceiveTimeout =>
      connectionTimeout()
    case Failure(ex) =>
      // async-dns responds with a Failure on DNS server lookup failure
      reportConnectFailure {
        throw new RuntimeException(ex)
      }
  }

  def register(address: InetSocketAddress, registration: ChannelRegistration): Unit = {
    reportConnectFailure {
      log.debug("Attempting connection to [{}]", address)
      if (channel.connect(address))
        completeConnect(registration, commander, options)
      else {
        registration.enableInterest(SelectionKey.OP_CONNECT)
        context.become(connecting(registration, tcp.Settings.FinishConnectRetries))
      }
    }
  }

  def connecting(registration: ChannelRegistration, remainingFinishConnectRetries: Int): Receive = {
    {
      case ChannelConnectable =>
        reportConnectFailure {
          if (channel.finishConnect()) {
            if (timeout.isDefined) context.setReceiveTimeout(Duration.Undefined) // Clear the timeout
            log.debug("Connection established to [{}]", remoteAddress)
            completeConnect(registration, commander, options)
          } else {
            if (remainingFinishConnectRetries > 0) {
              timers.startSingleTimer(RetryFinishConnect, RetryFinishConnect, 1.millisecond)
              context.become(connecting(registration, remainingFinishConnectRetries - 1))
            } else {
              log.debug(
                "Could not establish connection because finishConnect " +
                "never returned true (consider increasing pekko.io.tcp.finish-connect-retries)")
              stop(FinishConnectNeverReturnedTrueException)
            }
          }
        }
      case RetryFinishConnect =>
        reportConnectFailure {
          channelRegistry.register(channel, SelectionKey.OP_CONNECT)
        }
      case _: ReceiveTimeout =>
        connectionTimeout()
    }
  }

  private def connectionTimeout(): Unit = {
    if (timeout.isDefined) context.setReceiveTimeout(Duration.Undefined) // Clear the timeout
    log.debug("Connect timeout expired, could not establish connection to [{}]", remoteAddress)
    stop(connectTimeoutExpired(timeout))
  }

  override def postStop(): Unit =
    transportTermination match {
      case None              => super.postStop()
      case Some(termination) =>
        try super.postStop()
        finally {
          try {
            // The actor can stop before its registration handle arrives. The owning selector
            // orders this barrier after all registrations and any close submitted by postStop.
            channelRegistry.cancelAndCloseObserved(
              channel,
              () => {
                if (channel.isOpen || channel.isRegistered)
                  termination.tryFailure(new IllegalStateException("The TCP transport was not fully cleaned up."))
                else termination.trySuccess(Done)
                ()
              })
          } catch {
            case NonFatal(e) =>
              termination.tryFailure(e)
              ()
          }
        }
    }
}

@InternalApi
private[io] object TcpOutgoingConnection {
  private def openChannel(transportTermination: Option[Promise[Done]]): SocketChannel =
    transportTermination match {
      case None              => SocketChannel.open().configureBlocking(false).asInstanceOf[SocketChannel]
      case Some(termination) =>
        val channel =
          try SocketChannel.open()
          catch {
            case NonFatal(e) =>
              termination.trySuccess(Done)
              throw e
          }
        try {
          channel.configureBlocking(false)
          channel
        } catch {
          case NonFatal(e) =>
            try channel.close()
            catch { case NonFatal(closeError) => e.addSuppressed(closeError) }
            if (channel.isOpen) termination.tryFailure(e)
            else termination.trySuccess(Done)
            throw e
        }
    }

  val FinishConnectNeverReturnedTrueException =
    new ConnectException("Could not establish connection because finishConnect never returned true") with NoStackTrace

  private case object RetryFinishConnect

  def connectTimeoutExpired(timeout: Option[FiniteDuration]) =
    new ConnectException(s"Connect timeout of $timeout expired") with NoStackTrace
}

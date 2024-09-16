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

package org.apache.pekko.remote

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.PekkoException
import pekko.Done
import pekko.actor._
import pekko.annotation.InternalStableApi
import pekko.event.LoggingAdapter
import pekko.util.{ unused, OptionVal }

/**
 * RemoteTransportException represents a general failure within a RemoteTransport,
 * such as inability to start, wrong configuration etc.
 */
@SerialVersionUID(1L)
class RemoteTransportException(message: String, cause: Throwable) extends PekkoException(message, cause) {
  def this(msg: String) = this(msg, null)
}

/**
 * [[RemoteTransportException]] without stack trace.
 */
@SerialVersionUID(1L)
class RemoteTransportExceptionNoStackTrace(message: String, cause: Throwable)
    extends RemoteTransportException(message, cause)
    with NoStackTrace

/**
 * INTERNAL API
 *
 * The remote transport is responsible for sending and receiving messages.
 * Each transport has an address, which it should provide in
 * Serialization.currentTransportInformation (thread-local) while serializing
 * actor references (which might also be part of messages). This address must
 * be available (i.e. fully initialized) by the time the first message is
 * received or when the start() method returns, whatever happens first.
 */
private[pekko] abstract class RemoteTransport(val system: ExtendedActorSystem, val provider: RemoteActorRefProvider) {

  /**
   * Shuts down the remoting
   */
  def shutdown(): Future[Done]

  /**
   * Address to be used in RootActorPath of refs generated for this transport.
   */
  def addresses: immutable.Set[Address]

  /**
   * The default transport address of the ActorSystem
   * @return The listen address of the default transport
   */
  def defaultAddress: Address

  /**
   * Resolves the correct local address to be used for contacting the given remote address
   * @param remote the remote address
   * @return the local address to be used for the given remote address
   */
  def localAddressForRemote(remote: Address): Address

  /**
   * Start up the transport, i.e. enable incoming connections.
   */
  def start(): Unit

  /**
   * Sends the given message to the recipient supplying the sender() if any
   */
  def send(message: Any, senderOption: OptionVal[ActorRef], recipient: RemoteActorRef): Unit

  /**
   * Sends a management command to the underlying transport stack. The call returns with a Future that indicates
   * if the command was handled successfully or dropped.
   * @param cmd Command message to send to the transports.
   * @return A Future that indicates when the message was successfully handled or dropped.
   */
  def managementCommand(@unused cmd: Any): Future[Boolean] = Future.successful(false)

  /**
   * A Logger that can be used to log issues that may occur
   */
  def log: LoggingAdapter

  /**
   * Marks a remote system as out of sync and prevents reconnects until the quarantine timeout elapses.
   * @param address Address of the remote system to be quarantined
   * @param uid UID of the remote system, if the uid is not defined it will not be a strong quarantine but
   *   the current endpoint writer will be stopped (dropping system messages) and the address will be gated
   */
  @InternalStableApi
  def quarantine(address: Address, uid: Option[Long], reason: String): Unit

}

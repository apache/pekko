/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery
package tcp

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.actor.setup.Setup
import pekko.util.ccompat._
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession

@ccompatUsedUntil213
trait SSLEngineProvider {

  def createServerSSLEngine(hostname: String, port: Int): SSLEngine

  def createClientSSLEngine(hostname: String, port: Int): SSLEngine

  /**
   * Verification that will be called after every successful handshake
   * to verify additional session information. Return `None` if valid
   * otherwise `Some` with explaining cause.
   */
  def verifyClientSession(hostname: String, session: SSLSession): Option[Throwable]

  /**
   * Verification that will be called after every successful handshake
   * to verify additional session information. Return `None` if valid
   * otherwise `Some` with explaining cause.
   */
  def verifyServerSession(hostname: String, session: SSLSession): Option[Throwable]

}

class SslTransportException(message: String, cause: Throwable) extends RuntimeException(message, cause)

object SSLEngineProviderSetup {

  /**
   * Scala API: factory for defining a `SSLEngineProvider` that is passed in when ActorSystem
   * is created rather than creating one from configured class name.
   */
  def apply(sslEngineProvider: ExtendedActorSystem => SSLEngineProvider): SSLEngineProviderSetup =
    new SSLEngineProviderSetup(sslEngineProvider)

  /**
   * Java API: factory for defining a `SSLEngineProvider` that is passed in when ActorSystem
   * is created rather than creating one from configured class name.
   */
  def create(
      sslEngineProvider: java.util.function.Function[ExtendedActorSystem, SSLEngineProvider]): SSLEngineProviderSetup =
    apply(sys => sslEngineProvider(sys))

}

/**
 * Setup for defining a `SSLEngineProvider` that is passed in when ActorSystem
 * is created rather than creating one from configured class name. That is useful
 * when the SSLEngineProvider implementation require other external constructor parameters
 * or is created before the ActorSystem is created.
 *
 * Constructor is *Internal API*, use factories in [[SSLEngineProviderSetup]]
 */
class SSLEngineProviderSetup private (val sslEngineProvider: ExtendedActorSystem => SSLEngineProvider) extends Setup

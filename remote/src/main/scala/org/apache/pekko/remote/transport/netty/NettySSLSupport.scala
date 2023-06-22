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

import scala.annotation.nowarn
import com.typesafe.config.Config
import org.jboss.netty.handler.ssl.SslHandler

import org.apache.pekko
import pekko.japi.Util._
import pekko.util.ccompat._

/**
 * INTERNAL API
 */
private[pekko] class SSLSettings(config: Config) {

  import config.getBoolean
  import config.getString
  import config.getStringList

  val SSLKeyStore = getString("key-store")
  val SSLTrustStore = getString("trust-store")
  val SSLKeyStorePassword = getString("key-store-password")
  val SSLKeyPassword = getString("key-password")

  val SSLTrustStorePassword = getString("trust-store-password")

  val SSLEnabledAlgorithms = immutableSeq(getStringList("enabled-algorithms")).to(Set)

  val SSLProtocol = getString("protocol")

  val SSLRandomNumberGenerator = getString("random-number-generator")

  val SSLRequireMutualAuthentication = getBoolean("require-mutual-authentication")

}

/**
 * INTERNAL API
 *
 * Used for adding SSL support to Netty pipeline.
 * The `SSLEngine` is created via the configured [[SSLEngineProvider]].
 */
@ccompatUsedUntil213
@nowarn("msg=deprecated")
private[pekko] object NettySSLSupport {

  /**
   * Construct a SSLHandler which can be inserted into a Netty server/client pipeline
   */
  def apply(sslEngineProvider: SSLEngineProvider, isClient: Boolean): SslHandler = {
    val sslEngine =
      if (isClient) sslEngineProvider.createClientSSLEngine()
      else sslEngineProvider.createServerSSLEngine()
    new SslHandler(sslEngine)
  }
}

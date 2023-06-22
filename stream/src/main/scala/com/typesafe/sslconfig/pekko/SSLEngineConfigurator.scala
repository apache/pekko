/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package com.typesafe.sslconfig.pekko

import javax.net.ssl.{ SSLContext, SSLEngine }

import com.typesafe.sslconfig.ssl.SSLConfigSettings

/**
 * Gives the chance to configure the SSLContext before it is going to be used.
 * The passed in context will be already set in client mode and provided with hostInfo during initialization.
 */
@deprecated("Use Tcp and TLS with SSLEngine parameters instead. Setup the SSLEngine with needed parameters.",
  "Akka 2.6.0")
trait SSLEngineConfigurator {
  def configure(engine: SSLEngine, sslContext: SSLContext): SSLEngine
}

@deprecated("Use Tcp and TLS with SSLEngine parameters instead. Setup the SSLEngine with needed parameters.",
  "Akka 2.6.0")
final class DefaultSSLEngineConfigurator(
    config: SSLConfigSettings,
    enabledProtocols: Array[String],
    enabledCipherSuites: Array[String])
    extends SSLEngineConfigurator {
  config ne null // @unused unavailable
  def configure(engine: SSLEngine, sslContext: SSLContext): SSLEngine = {
    engine.setSSLParameters(sslContext.getDefaultSSLParameters)
    engine.setEnabledProtocols(enabledProtocols)
    engine.setEnabledCipherSuites(enabledCipherSuites)
    engine
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.ekrich.config.{ Config, ConfigFactory }

object ArterySpecSupport {
  // same for all artery enabled remoting tests
  private val staticArteryRemotingConfig = ConfigFactory.parseString(s"""
    pekko {
      actor {
        provider = remote
      }
      remote.warn-about-direct-use = off
      remote.artery {
        enabled = on
        canonical {
          hostname = localhost
          port = 0
        }
      }
    }""")

  /**
   * Artery enabled, flight recorder enabled, dynamic selection of port on localhost.
   */
  def defaultConfig: Config =
    staticArteryRemotingConfig.withFallback(tlsConfig) // TLS only used if transport=tls-tcp

  // set the test key-store and trust-store properties
  // TLS only used if transport=tls-tcp, which can be set from specific tests or
  // System properties (e.g. jenkins job)
  // TODO: randomly return a Config using ConfigSSLEngineProvider or
  //  RotatingKeysSSLEngineProvider and, eventually, run tests twice
  //  (once for each provider).
  lazy val tlsConfig: Config = {
    import org.apache.pekko.testkit.PekkoSpec._
    val trustStore = resourcePath("truststore")
    val keyStore = resourcePath("keystore")

    ConfigFactory.parseString(s"""
      pekko.remote.artery.ssl.config-ssl-engine {
        key-store = "$keyStore"
        trust-store = "$trustStore"
      }
    """)
  }

}

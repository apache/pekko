/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.remote.artery.tcp

import org.apache.pekko

import com.typesafe.config.ConfigFactory

import pekko.testkit.PekkoSpec

class ArteryConfigSSLEngineProviderSpec
    extends PekkoSpec(
      ConfigFactory.parseString("""
    pekko {
      actor.provider = remote
      remote.artery.ssl.config-ssl-engine {
        key-store = "/nonexistent/keystore.jks"
        key-store-password = "changeme"
        key-password = "changeme"
        trust-store = "/nonexistent/truststore.jks"
        trust-store-password = "changeme"
        protocol = "TLSv1.3"
        enabled-algorithms = [TLS_AES_128_GCM_SHA256]
        random-number-generator = ""
        require-mutual-authentication = off
      }
    }
  """)) {

  "Artery ConfigSSLEngineProvider" must {
    "fail fast when keystore cannot be loaded" in {
      intercept[SslTransportException] {
        new ConfigSSLEngineProvider(system)
      }.getMessage should include("could not be established")
    }
  }
}

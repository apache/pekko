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

package org.apache.pekko.remote

import org.apache.pekko
import pekko.remote.transport.netty.SSLSettings
import pekko.testkit._

class Ticket1978ConfigSpec extends PekkoSpec("""
    pekko.remote.classic.netty.ssl.security {
      random-number-generator = "SecureRandom"
    }
    """) with ImplicitSender with DefaultTimeout {

  "SSL Remoting" must {
    "be able to parse these extra Netty config elements" in {
      val settings = new SSLSettings(system.settings.config.getConfig("pekko.remote.classic.netty.ssl.security"))

      settings.SSLKeyStore should ===("keystore")
      settings.SSLKeyStorePassword should ===("changeme")
      settings.SSLKeyPassword should ===("changeme")
      settings.SSLTrustStore should ===("truststore")
      settings.SSLTrustStorePassword should ===("changeme")
      settings.SSLProtocol should ===("TLSv1.2")
      settings.SSLEnabledAlgorithms should ===(
        Set("TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"))
      settings.SSLRandomNumberGenerator should ===("SecureRandom")
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.discovery

import org.apache.pekko.testkit.PekkoSpec
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.Future

object DnsDiscoveryDocSpec {
  val config = ConfigFactory.parseString("""
    // #configure-dns
    pekko {
      discovery {
        method = pekko-dns
      }
    }
    // #configure-dns
    """)
}

class DnsDiscoveryDocSpec extends PekkoSpec(DnsDiscoveryDocSpec.config) {

  "DNS Discovery" should {
    "find pekko.io" in {
      // #lookup-dns
      import org.apache.pekko
      import pekko.discovery.Discovery
      import pekko.discovery.ServiceDiscovery

      val discovery: ServiceDiscovery = Discovery(system).discovery
      // ...
      val result: Future[ServiceDiscovery.Resolved] = discovery.lookup("pekko.io", resolveTimeout = 3.seconds)
      // #lookup-dns

      try {
        val resolved = result.futureValue
        resolved.serviceName shouldBe "pekko.io"
        resolved.addresses shouldNot be(Symbol("empty"))
      } catch {
        case e: Exception =>
          info("Failed lookup pekko.io, but ignoring: " + e)
          pending
      }
    }
  }

}

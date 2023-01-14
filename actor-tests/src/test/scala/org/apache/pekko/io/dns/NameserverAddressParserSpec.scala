/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns

import java.net.InetSocketAddress

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NameserverAddressParserSpec extends AnyWordSpec with Matchers {
  "Parser" should {
    "handle explicit port in IPv4 address" in {
      DnsSettings.parseNameserverAddress("8.8.8.8:153") shouldEqual new InetSocketAddress("8.8.8.8", 153)
    }
    "handle explicit port in IPv6 address" in {
      DnsSettings.parseNameserverAddress("[2001:4860:4860::8888]:153") shouldEqual new InetSocketAddress(
        "2001:4860:4860::8888",
        153)
    }
    "handle default port in IPv4 address" in {
      DnsSettings.parseNameserverAddress("8.8.8.8") shouldEqual new InetSocketAddress("8.8.8.8", 53)
    }
    "handle default port in IPv6 address" in {
      DnsSettings.parseNameserverAddress("[2001:4860:4860::8888]") shouldEqual new InetSocketAddress(
        "2001:4860:4860::8888",
        53)
    }
  }
}

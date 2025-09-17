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

package org.apache.pekko.io.dns.internal

import org.apache.pekko
import pekko.io.Dns
import pekko.io.dns.AAAARecord
import pekko.io.dns.CachePolicy.Ttl
import pekko.io.dns.DnsProtocol.{ Resolve, Resolved }
import pekko.testkit.{ ImplicitSender, PekkoSpec, WithLogCapturing }

class AsyncDnsManagerSpec extends PekkoSpec("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.io.dns.resolver = async-dns
    pekko.io.dns.async-dns.nameservers = default
  """) with ImplicitSender with WithLogCapturing {

  val dns = Dns(system).manager

  "Async DNS Manager" must {
    "support ipv6" in {
      dns ! Resolve("::1") // ::1 will short circuit the resolution
      expectMsgType[Resolved] match {
        case Resolved("::1", Seq(AAAARecord("::1", Ttl.effectivelyForever, _)), Nil) =>
        case other                                                                   => fail(other.toString)
      }
    }

    "provide access to cache" in {
      dns ! AsyncDnsManager.GetCache
      ((expectMsgType[pekko.io.SimpleDnsCache]: pekko.io.SimpleDnsCache) should be).theSameInstanceAs(Dns(system).cache)
    }
  }

}

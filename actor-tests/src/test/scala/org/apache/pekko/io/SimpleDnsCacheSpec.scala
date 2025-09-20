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

package org.apache.pekko.io

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

import scala.collection.immutable
import scala.concurrent.duration._

import org.apache.pekko
import pekko.io.dns.ARecord
import pekko.io.dns.CachePolicy.Ttl
import pekko.io.dns.DnsProtocol
import pekko.io.dns.DnsProtocol.Ip

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SimpleDnsCacheSpec extends AnyWordSpec with Matchers {
  "Cache" should {
    "not reply with expired but not yet swept out entries" in {
      val localClock = new AtomicLong(0)
      val cache: SimpleDnsCache = new SimpleDnsCache() {
        override protected def clock() = localClock.get
      }
      val ttl = Ttl.fromPositive(5000.millis)
      val cacheEntry = DnsProtocol.Resolved(
        "test.local",
        immutable.Seq(ARecord("test.local", ttl, InetAddress.getByName("127.0.0.1"))))
      cache.put(("test.local", Ip()), cacheEntry, ttl)

      cache.cached(DnsProtocol.Resolve("test.local")) should ===(Some(cacheEntry))
      localClock.set(4999)
      cache.cached(DnsProtocol.Resolve("test.local")) should ===(Some(cacheEntry))
      localClock.set(5000)
      cache.cached(DnsProtocol.Resolve("test.local")) should ===(None)
    }

    "sweep out expired entries on cleanup()" in {
      val localClock = new AtomicLong(0)
      val cache: SimpleDnsCache = new SimpleDnsCache() {
        override protected def clock() = localClock.get
      }
      val ttl = Ttl.fromPositive(5000.millis)
      val cacheEntry =
        DnsProtocol.Resolved(
          "test.local",
          immutable.Seq(ARecord("test.local", ttl, InetAddress.getByName("127.0.0.1"))))
      cache.put(("test.local", Ip()), cacheEntry, ttl)

      cache.cached(DnsProtocol.Resolve("test.local")) should ===(Some(cacheEntry))
      localClock.set(5000)
      cache.cached(DnsProtocol.Resolve("test.local")) should ===(None)
      localClock.set(0)
      cache.cached(DnsProtocol.Resolve("test.local")) should ===(Some(cacheEntry))
      localClock.set(5000)
      cache.cleanup()
      cache.cached(DnsProtocol.Resolve("test.local")) should ===(None)
      localClock.set(0)
      cache.cached(DnsProtocol.Resolve("test.local")) should ===(None)
    }

  }

  // TODO test that the old protocol is converted correctly
}

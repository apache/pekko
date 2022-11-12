/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import scala.annotation.nowarn

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.io._

/**
 * INTERNAL API
 */
@InternalApi
@nowarn("msg=deprecated")
private[pekko] class AsyncDnsProvider extends DnsProvider {
  override def cache: Dns = new SimpleDnsCache()
  override def actorClass = classOf[AsyncDnsResolver]
  override def managerClass = classOf[AsyncDnsManager]
}

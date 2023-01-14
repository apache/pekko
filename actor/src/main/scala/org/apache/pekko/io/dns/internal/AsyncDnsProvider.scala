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

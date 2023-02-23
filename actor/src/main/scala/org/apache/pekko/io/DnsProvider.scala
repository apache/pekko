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

package org.apache.pekko.io

import org.apache.pekko
import pekko.actor.Actor

/**
 * Where as it is possible to plug in alternative DNS implementations it is not recommended.
 *
 * It is expected that this will be deprecated/removed in future Apache Pekko versions
 *
 *  TODO make private and remove deprecated in v1.1.0
 */
@deprecated("Overriding the DNS implementation will be removed in future versions of Akka", "Akka 2.6.0")
trait DnsProvider {

  /**
   * Cache implementation that can be accessed via Dns(system) to avoid asks to the resolver actors.
   * It is not recommended to override the default SimpleDnsCache
   */
  def cache: Dns = new SimpleDnsCache()

  /**
   * DNS resolver actor. Should respond to [[pekko.io.dns.DnsProtocol.Resolve]] with
   * [[pekko.io.dns.DnsProtocol.Resolved]]
   */
  def actorClass: Class[_ <: Actor]

  /**
   * DNS manager class. Is responsible for creating resolvers and doing any cache cleanup.
   * The DNS extension will create one of these Actors. It should have a ctr that accepts
   * a [[DnsExt]]
   */
  def managerClass: Class[_ <: Actor]
}

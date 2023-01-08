/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease.javadsl

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import pekko.actor.ClassicActorSystemProvider
import pekko.coordination.lease.internal.LeaseAdapter
import pekko.coordination.lease.internal.LeaseAdapterToScala
import pekko.coordination.lease.scaladsl.{ LeaseProvider => ScalaLeaseProvider }

object LeaseProvider extends ExtensionId[LeaseProvider] with ExtensionIdProvider {
  override def get(system: ActorSystem): LeaseProvider = super.get(system)
  override def get(system: ClassicActorSystemProvider): LeaseProvider = super.get(system)

  override def lookup = LeaseProvider

  override def createExtension(system: ExtendedActorSystem): LeaseProvider = new LeaseProvider(system)

  private final case class LeaseKey(leaseName: String, configPath: String, clientName: String)
}

class LeaseProvider(system: ExtendedActorSystem) extends Extension {
  private val delegate = ScalaLeaseProvider(system)

  /**
   * The configuration define at `configPath` must have a property `lease-class` that defines
   * the fully qualified class name of the Lease implementation.
   * The class must implement [[Lease]] and have constructor with [[pekko.coordination.lease.LeaseSettings]] parameter and
   * optionally ActorSystem parameter.
   *
   * @param leaseName the name of the lease resource
   * @param configPath the path of configuration for the lease
   * @param ownerName the owner that will `acquire` the lease, e.g. hostname and port of the ActorSystem
   */
  def getLease(leaseName: String, configPath: String, ownerName: String): Lease = {
    val scalaLease = delegate.getLease(leaseName, configPath, ownerName)
    // unwrap if this is a java implementation
    scalaLease match {
      case adapter: LeaseAdapterToScala => adapter.delegate
      case _                            => new LeaseAdapter(scalaLease)(system.dispatchers.internalDispatcher)
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.external

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }

import org.apache.pekko
import pekko.actor.{ ClassicActorSystemProvider, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import pekko.annotation.ApiMayChange
import pekko.cluster.sharding.external.internal.ExternalShardAllocationClientImpl

/**
 * API May Change
 */
@ApiMayChange
final class ExternalShardAllocation(system: ExtendedActorSystem) extends Extension {

  private val clients = new ConcurrentHashMap[String, ExternalShardAllocationClientImpl]

  private val factory = new JFunction[String, ExternalShardAllocationClientImpl] {
    override def apply(typeName: String): ExternalShardAllocationClientImpl =
      new ExternalShardAllocationClientImpl(system, typeName)
  }

  /**
   * Scala API
   */
  def clientFor(typeName: String): scaladsl.ExternalShardAllocationClient = client(typeName)

  /**
   * Java API
   */
  def getClient(typeName: String): javadsl.ExternalShardAllocationClient = client(typeName)

  private def client(typeName: String): ExternalShardAllocationClientImpl = {
    clients.computeIfAbsent(typeName, factory)
  }
}

object ExternalShardAllocation extends ExtensionId[ExternalShardAllocation] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): ExternalShardAllocation =
    new ExternalShardAllocation(system)

  override def lookup: ExternalShardAllocation.type = ExternalShardAllocation

  override def get(system: ClassicActorSystemProvider): ExternalShardAllocation = super.get(system)
}

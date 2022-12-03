/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.external

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
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

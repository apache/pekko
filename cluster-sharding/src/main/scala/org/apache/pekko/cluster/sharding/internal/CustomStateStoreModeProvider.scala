/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.internal
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ClusterShardingSettings
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.event.Logging

/**
 * INTERNAL API
 *
 * Only intended for testing, not an extension point.
 */
@InternalApi
private[pekko] final class CustomStateStoreModeProvider(
    typeName: String,
    system: ActorSystem,
    settings: ClusterShardingSettings)
    extends RememberEntitiesProvider {

  private val log = Logging(system, classOf[CustomStateStoreModeProvider])
  log.warning("Using custom remember entities store for [{}], not intended for production use.", typeName)
  val customStore = if (system.settings.config.hasPath("pekko.cluster.sharding.remember-entities-custom-store")) {
    val customClassName = system.settings.config.getString("pekko.cluster.sharding.remember-entities-custom-store")

    val store = system
      .asInstanceOf[ExtendedActorSystem]
      .dynamicAccess
      .createInstanceFor[RememberEntitiesProvider](
        customClassName,
        Vector((classOf[ClusterShardingSettings], settings), (classOf[String], typeName)))
    log.debug("Will use custom remember entities store provider [{}]", store)
    store.get

  } else {
    log.error("Missing custom store class configuration for CustomStateStoreModeProvider")
    throw new RuntimeException("Missing custom store class configuration")
  }

  override def shardStoreProps(shardId: ShardId): Props = customStore.shardStoreProps(shardId)

  override def coordinatorStoreProps(): Props = customStore.coordinatorStoreProps()
}

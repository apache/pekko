/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed.internal.receptionist

import scala.concurrent.duration._
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }
import com.typesafe.config.Config
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.cluster.ddata.Key.KeyId
import pekko.cluster.ddata.Replicator
import pekko.cluster.ddata.Replicator.WriteConsistency
import pekko.cluster.ddata.ReplicatorSettings
import pekko.util.Helpers.toRootLowerCase

/**
 * Internal API
 */
@InternalApi
private[pekko] object ClusterReceptionistSettings {
  def apply(system: ActorSystem[_]): ClusterReceptionistSettings =
    apply(system.settings.config.getConfig("pekko.cluster.typed.receptionist"))

  def apply(config: Config): ClusterReceptionistSettings = {
    val writeTimeout = 5.seconds // the timeout is not important
    val writeConsistency = {
      val key = "write-consistency"
      toRootLowerCase(config.getString(key)) match {
        case "local"    => Replicator.WriteLocal
        case "majority" => Replicator.WriteMajority(writeTimeout)
        case "all"      => Replicator.WriteAll(writeTimeout)
        case _          => Replicator.WriteTo(config.getInt(key), writeTimeout)
      }
    }

    val replicatorSettings = ReplicatorSettings(config.getConfig("distributed-data"))

    // Having durable store of entries does not make sense for receptionist, as registered
    // services does not survive a full cluster stop, make sure that it is disabled
    val replicatorSettingsWithoutDurableStore = replicatorSettings.withDurableKeys(Set.empty[KeyId])

    ClusterReceptionistSettings(
      writeConsistency,
      pruningInterval = config.getDuration("pruning-interval", MILLISECONDS).millis,
      pruneRemovedOlderThan = config.getDuration("prune-removed-older-than", MILLISECONDS).millis,
      config.getInt("distributed-key-count"),
      replicatorSettingsWithoutDurableStore)
  }
}

/**
 * Internal API
 */
@InternalApi
private[pekko] case class ClusterReceptionistSettings(
    writeConsistency: WriteConsistency,
    pruningInterval: FiniteDuration,
    pruneRemovedOlderThan: FiniteDuration,
    distributedKeyCount: Int,
    replicatorSettings: ReplicatorSettings)

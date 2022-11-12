/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.javadsl;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings;

public class ClusterShardingSettingsCompileOnly {

  static void shouldBeUsableFromJava() {
    ActorSystem<?> system = null;
    ClusterShardingSettings.StateStoreMode mode = ClusterShardingSettings.stateStoreModeDdata();
    ClusterShardingSettings.create(system)
        .withStateStoreMode(mode)
        .withRememberEntitiesStoreMode(ClusterShardingSettings.rememberEntitiesStoreModeDdata());
  }
}

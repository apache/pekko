/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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

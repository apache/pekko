/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import com.typesafe.config.ConfigFactory

import org.apache.pekko.persistence.{ PersistentActor, RuntimePluginConfig }

object PersistenceMultiDocSpec {

  val DefaultConfig =
    """
  //#default-config
  # Absolute path to the default journal plugin configuration entry.
  pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
  # Absolute path to the default snapshot store plugin configuration entry.
  pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
  //#default-config
  """

  // #default-plugins
  trait ActorWithDefaultPlugins extends PersistentActor {
    override def persistenceId = "123"
  }

  // #default-plugins

  val OverrideConfig =
    s"""
  //#override-config
  # Configuration entry for the custom journal plugin, see `journalPluginId`.
  pekko.persistence.chronicle.journal {
    # Standard persistence extension property: provider FQCN.
    class = "org.apache.pekko.persistence.chronicle.ChronicleSyncJournal"
    # Custom setting specific for the journal `ChronicleSyncJournal`.
    folder = $${user.dir}/store/journal
  }
  # Configuration entry for the custom snapshot store plugin, see `snapshotPluginId`.
  pekko.persistence.chronicle.snapshot-store {
    # Standard persistence extension property: provider FQCN.
    class = "org.apache.pekko.persistence.chronicle.ChronicleSnapshotStore"
    # Custom setting specific for the snapshot store `ChronicleSnapshotStore`.
    folder = $${user.dir}/store/snapshot
  }
  //#override-config
  """

  // #override-plugins
  trait ActorWithOverridePlugins extends PersistentActor {
    override def persistenceId = "123"

    // Absolute path to the journal plugin configuration entry in the `reference.conf`.
    override def journalPluginId = "pekko.persistence.chronicle.journal"

    // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`.
    override def snapshotPluginId = "pekko.persistence.chronicle.snapshot-store"
  }

  // #override-plugins

  // #runtime-config
  trait ActorWithRuntimePluginConfig extends PersistentActor with RuntimePluginConfig {
    // Variable that is retrieved at runtime, from an external service for instance.
    val runtimeDistinction = "foo"

    override def persistenceId = "123"

    // Absolute path to the journal plugin configuration entry, not defined in the `reference.conf`.
    override def journalPluginId = s"journal-plugin-$runtimeDistinction"

    // Absolute path to the snapshot store plugin configuration entry, not defined in the `reference.conf`.
    override def snapshotPluginId = s"snapshot-store-plugin-$runtimeDistinction"

    // Configuration which contains the journal plugin id defined above
    override def journalPluginConfig =
      ConfigFactory
        .empty()
        .withValue(
          s"journal-plugin-$runtimeDistinction",
          context.system.settings.config
            .getValue("journal-plugin") // or a very different configuration coming from an external service.
        )

    // Configuration which contains the snapshot store plugin id defined above
    override def snapshotPluginConfig =
      ConfigFactory
        .empty()
        .withValue(
          s"snapshot-plugin-$runtimeDistinction",
          context.system.settings.config
            .getValue("snapshot-store-plugin") // or a very different configuration coming from an external service.
        )

  }

  // #runtime-config
}

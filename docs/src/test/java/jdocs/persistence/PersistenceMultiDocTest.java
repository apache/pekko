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

package jdocs.persistence;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.persistence.AbstractPersistentActor;
import org.apache.pekko.persistence.RuntimePluginConfig;

public class PersistenceMultiDocTest {

  // #default-plugins
  abstract class AbstractPersistentActorWithDefaultPlugins extends AbstractPersistentActor {
    @Override
    public String persistenceId() {
      return "123";
    }
  }
  // #default-plugins

  // #override-plugins
  abstract class AbstractPersistentActorWithOverridePlugins extends AbstractPersistentActor {
    @Override
    public String persistenceId() {
      return "123";
    }

    // Absolute path to the journal plugin configuration entry in the `reference.conf`
    @Override
    public String journalPluginId() {
      return "pekko.persistence.chronicle.journal";
    }

    // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`
    @Override
    public String snapshotPluginId() {
      return "pekko.persistence.chronicle.snapshot-store";
    }
  }
  // #override-plugins

  // #runtime-config
  abstract class AbstractPersistentActorWithRuntimePluginConfig extends AbstractPersistentActor
      implements RuntimePluginConfig {
    // Variable that is retrieved at runtime, from an external service for instance.
    String runtimeDistinction = "foo";

    @Override
    public String persistenceId() {
      return "123";
    }

    // Absolute path to the journal plugin configuration entry in the `reference.conf`
    @Override
    public String journalPluginId() {
      return "journal-plugin-" + runtimeDistinction;
    }

    // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`
    @Override
    public String snapshotPluginId() {
      return "snapshot-store-plugin-" + runtimeDistinction;
    }

    // Configuration which contains the journal plugin id defined above
    @Override
    public Config journalPluginConfig() {
      return ConfigFactory.empty()
          .withValue(
              "journal-plugin-" + runtimeDistinction,
              getContext()
                  .getSystem()
                  .settings()
                  .config()
                  .getValue(
                      "journal-plugin") // or a very different configuration coming from an external
              // service.
              );
    }

    // Configuration which contains the snapshot store plugin id defined above
    @Override
    public Config snapshotPluginConfig() {
      return ConfigFactory.empty()
          .withValue(
              "snapshot-plugin-" + runtimeDistinction,
              getContext()
                  .getSystem()
                  .settings()
                  .config()
                  .getValue(
                      "snapshot-store-plugin") // or a very different configuration coming from an
              // external service.
              );
    }
  }
  // #runtime-config

}

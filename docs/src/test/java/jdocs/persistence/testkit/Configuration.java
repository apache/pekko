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

package jdocs.persistence.testkit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin;
import org.apache.pekko.persistence.testkit.PersistenceTestKitSnapshotPlugin;
import org.apache.pekko.persistence.testkit.javadsl.PersistenceTestKit;
import org.apache.pekko.persistence.testkit.javadsl.SnapshotTestKit;

public class Configuration {

  // #testkit-typed-conf
  public class PersistenceTestKitConfig {

    Config conf =
        PersistenceTestKitPlugin.getInstance()
            .config()
            .withFallback(ConfigFactory.defaultApplication());

    ActorSystem<Command> system = ActorSystem.create(new SomeBehavior(), "example", conf);

    PersistenceTestKit testKit = PersistenceTestKit.create(system);
  }
  // #testkit-typed-conf

  // #snapshot-typed-conf
  public class SnapshotTestKitConfig {

    Config conf =
        PersistenceTestKitSnapshotPlugin.getInstance()
            .config()
            .withFallback(ConfigFactory.defaultApplication());

    ActorSystem<Command> system = ActorSystem.create(new SomeBehavior(), "example", conf);

    SnapshotTestKit testKit = SnapshotTestKit.create(system);
  }
  // #snapshot-typed-conf

}

class SomeBehavior extends Behavior<Command> {
  public SomeBehavior() {
    super(1);
  }
}

class Command {}

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

package jdocs.config;

// #imports
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.ekrich.config.Config;
import org.ekrich.config.ConfigFactory;

// #imports
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;

public class ConfigDocTest {

  private Behavior<Void> rootBehavior = Behaviors.empty();

  public void customConfig() {
    // #custom-config
    Config customConf = ConfigFactory.parseString("pekko.log-config-on-start = on");
    // ConfigFactory.load sandwiches customConfig between default reference
    // config and default overrides, and then resolves it.
    ActorSystem<Void> system =
        ActorSystem.create(rootBehavior, "MySystem", ConfigFactory.load(customConf));
    // #custom-config

    ActorTestKit.shutdown(system);
  }

  public void compileOnlyPrintConfig() {
    // #dump-config
    ActorSystem<Void> system = ActorSystem.create(rootBehavior, "MySystem");
    system.logConfiguration();
    // #dump-config
  }

  public void compileOnlySeparateApps() {
    // #separate-apps
    Config config = ConfigFactory.load();
    ActorSystem<Void> app1 =
        ActorSystem.create(rootBehavior, "MyApp1", config.getConfig("myapp1").withFallback(config));
    ActorSystem<Void> app2 =
        ActorSystem.create(
            rootBehavior,
            "MyApp2",
            config.getConfig("myapp2").withOnlyPath("pekko").withFallback(config));
    // #separate-apps
  }

  public ActorSystem createConfiguredSystem() {
    // #custom-config-2
    // make a Config with just your special setting
    Config myConfig = ConfigFactory.parseString("something=somethingElse");
    // load the normal config stack (system props,
    // then application.conf, then reference.conf)
    Config regularConfig = ConfigFactory.load();
    // override regular stack with myConfig
    Config combined = myConfig.withFallback(regularConfig);
    // put the result in between the overrides
    // (system props) and defaults again
    Config complete = ConfigFactory.load(combined);
    // create ActorSystem
    ActorSystem system = ActorSystem.create(rootBehavior, "myname", complete);
    // #custom-config-2
    return system;
  }
}

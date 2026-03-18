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

package org.apache.pekko.cluster.sharding.typed.javadsl;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.ShardCoordinator;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.typed.ShardedDaemonProcessCommand;
import org.apache.pekko.cluster.sharding.typed.ShardedDaemonProcessSettings;

public class ShardedDaemonProcessCompileOnlyTest {

  interface Command {}

  enum Stop implements Command {
    INSTANCE
  }

  {
    ActorSystem<Void> system = null;
    ShardedDaemonProcess.get(system).init(Command.class, "MyName", 8, id -> Behaviors.empty());

    ShardedDaemonProcess.get(system)
        .init(
            Command.class,
            "MyName",
            8,
            id -> Behaviors.empty(),
            ShardedDaemonProcessSettings.create(system),
            Optional.of(Stop.INSTANCE));

    // #tag-processing
    List<String> tags = Arrays.asList("tag-1", "tag-2", "tag-3");
    ShardedDaemonProcess.get(system)
        .init(
            TagProcessor.Command.class,
            "TagProcessors",
            tags.size(),
            id -> TagProcessor.create(tags.get(id)));
    // #tag-processing
  }

  void coverAllFactories(ActorSystem<Void> system) {
    ShardedDaemonProcess.get(system)
        .init(TagProcessor.Command.class, "name", 8, id -> Behaviors.empty());

    ShardedDaemonProcessSettings settings =
        ShardedDaemonProcessSettings.create(system)
            .withKeepAliveFromNumberOfNodes(7)
            .withKeepAliveInterval(Duration.ofSeconds(2))
            .withKeepAliveThrottleInterval(Duration.ofMillis(200))
            .withShardingSettings(ClusterShardingSettings.create(system));

    ShardedDaemonProcess.get(system)
        .init(
            TagProcessor.Command.class,
            "name",
            8,
            id -> Behaviors.empty(),
            settings,
            Optional.empty());

    ShardedDaemonProcess.get(system)
        .init(
            TagProcessor.Command.class,
            "name",
            8,
            id -> Behaviors.empty(),
            settings,
            Optional.empty(),
            Optional.of(new ShardCoordinator.LeastShardAllocationStrategy(10, 100)));

    ActorRef<ShardedDaemonProcessCommand> control1 =
        ShardedDaemonProcess.get(system)
            .initWithContext(TagProcessor.Command.class, "name", 8, ctx -> Behaviors.empty());

    ActorRef<ShardedDaemonProcessCommand> control2 =
        ShardedDaemonProcess.get(system)
            .initWithContext(
                TagProcessor.Command.class,
                "name",
                8,
                ctx -> Behaviors.empty(),
                settings,
                Optional.empty());

    ActorRef<ShardedDaemonProcessCommand> control3 =
        ShardedDaemonProcess.get(system)
            .initWithContext(
                TagProcessor.Command.class,
                "name",
                8,
                ctx -> Behaviors.empty(),
                settings,
                Optional.empty(),
                Optional.empty());
  }

  static class TagProcessor {
    interface Command {}

    static Behavior<Command> create(String tag) {
      return Behaviors.setup(
          context -> {
            // ... start the tag processing ...
            return Behaviors.empty();
          });
    }
  }
}

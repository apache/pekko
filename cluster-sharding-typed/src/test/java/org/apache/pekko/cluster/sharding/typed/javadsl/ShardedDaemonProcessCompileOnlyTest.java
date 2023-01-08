/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.javadsl;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardedDaemonProcessSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

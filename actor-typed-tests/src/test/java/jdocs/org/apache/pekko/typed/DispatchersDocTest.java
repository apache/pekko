/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.typed;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.DispatcherSelector;
import org.apache.pekko.actor.typed.javadsl.*;

public class DispatchersDocTest {

  public static void spawnDispatchers(ActorContext<Integer> context, Behavior<String> behavior) {
    // #spawn-dispatcher
    context.spawn(behavior, "DefaultDispatcher");
    context.spawn(behavior, "ExplicitDefaultDispatcher", DispatcherSelector.defaultDispatcher());
    context.spawn(behavior, "BlockingDispatcher", DispatcherSelector.blocking());
    context.spawn(behavior, "ParentDispatcher", DispatcherSelector.sameAsParent());
    context.spawn(
        behavior, "DispatcherFromConfig", DispatcherSelector.fromConfig("your-dispatcher"));
    // #spawn-dispatcher
  }

  public static void spawnDispatchersWithInteroperability(
      ActorContext<Integer> context, Behavior<String> behavior) {
    // #interoperability-with-mailbox
    context.spawn(
        behavior,
        "ExplicitDefaultDispatcher",
        DispatcherSelector.defaultDispatcher().withMailboxFromConfig("my-app.my-special-mailbox"));
    context.spawn(
        behavior,
        "BlockingDispatcher",
        DispatcherSelector.blocking().withMailboxFromConfig("my-app.my-special-mailbox"));
    context.spawn(
        behavior,
        "ParentDispatcher",
        DispatcherSelector.sameAsParent().withMailboxFromConfig("my-app.my-special-mailbox"));
    context.spawn(
        behavior,
        "DispatcherFromConfig",
        DispatcherSelector.fromConfig("your-dispatcher")
            .withMailboxFromConfig("my-app.my-special-mailbox"));
    // #interoperability-with-mailbox
  }
}

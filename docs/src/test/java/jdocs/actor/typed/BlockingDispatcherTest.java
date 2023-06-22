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

package jdocs.actor.typed;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

public class BlockingDispatcherTest {
  public static void main(String args[]) {
    // #blocking-main
    Behavior<Void> root =
        Behaviors.setup(
            context -> {
              for (int i = 0; i < 100; i++) {
                context.spawn(BlockingActor.create(), "BlockingActor-" + i).tell(i);
                context.spawn(PrintActor.create(), "PrintActor-" + i).tell(i);
              }
              return Behaviors.ignore();
            });
    // #blocking-main

    ActorSystem<Void> system = ActorSystem.<Void>create(root, "BlockingDispatcherTest");
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.actor.typed;

// #blocking-in-actor
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

public class BlockingActor extends AbstractBehavior<Integer> {
  public static Behavior<Integer> create() {
    return Behaviors.setup(BlockingActor::new);
  }

  private BlockingActor(ActorContext<Integer> context) {
    super(context);
  }

  @Override
  public Receive<Integer> createReceive() {
    return newReceiveBuilder()
        .onMessage(
            Integer.class,
            i -> {
              // DO NOT DO THIS HERE: this is an example of incorrect code,
              // better alternatives are described further on.

              // block for 5 seconds, representing blocking I/O, etc
              Thread.sleep(5000);
              System.out.println("Blocking operation finished: " + i);
              return Behaviors.same();
            })
        .build();
  }
}
// #blocking-in-actor

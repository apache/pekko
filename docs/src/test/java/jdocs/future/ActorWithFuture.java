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

package jdocs.future;

// #context-dispatcher
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.actor.AbstractActor;

public class ActorWithFuture extends AbstractActor {
  ActorWithFuture() {
    CompletableFuture.supplyAsync(() -> "hello", getContext().dispatcher());
  }

  @Override
  public Receive createReceive() {
    return AbstractActor.emptyBehavior();
  }
}
// #context-dispatcher

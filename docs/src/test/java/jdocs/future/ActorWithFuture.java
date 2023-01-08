/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.future;

// #context-dispatcher
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.dispatch.Futures;

public class ActorWithFuture extends AbstractActor {
  ActorWithFuture() {
    Futures.future(() -> "hello", getContext().dispatcher());
  }

  @Override
  public Receive createReceive() {
    return AbstractActor.emptyBehavior();
  }
}
// #context-dispatcher

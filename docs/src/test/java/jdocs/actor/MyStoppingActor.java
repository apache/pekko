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

package jdocs.actor;

// #my-stopping-actor
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;

public class MyStoppingActor extends AbstractActor {

  ActorRef child = null;

  // ... creation of child ...

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals("interrupt-child", m -> getContext().stop(child))
        .matchEquals("done", m -> getContext().stop(getSelf()))
        .build();
  }
}
// #my-stopping-actor

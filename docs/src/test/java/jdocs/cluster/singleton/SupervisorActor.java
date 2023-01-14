/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster.singleton;

// #singleton-supervisor-actor
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.AbstractActor.Receive;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.SupervisorStrategy;

public class SupervisorActor extends AbstractActor {
  final Props childProps;
  final SupervisorStrategy supervisorStrategy;
  final ActorRef child;

  SupervisorActor(Props childProps, SupervisorStrategy supervisorStrategy) {
    this.childProps = childProps;
    this.supervisorStrategy = supervisorStrategy;
    this.child = getContext().actorOf(childProps, "supervised-child");
  }

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return supervisorStrategy;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().matchAny(msg -> child.forward(msg, getContext())).build();
  }
}
// #singleton-supervisor-actor

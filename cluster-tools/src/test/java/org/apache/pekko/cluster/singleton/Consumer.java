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

package org.apache.pekko.cluster.singleton;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.cluster.singleton.TestSingletonMessages.*;

public class Consumer extends AbstractActor {

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  ActorRef queue;
  ActorRef delegateTo;
  int current = 0;
  boolean stoppedBeforeUnregistration = true;

  public Consumer(ActorRef _queue, ActorRef _delegateTo) {
    queue = _queue;
    delegateTo = _delegateTo;
  }

  @Override
  public void preStart() {
    queue.tell(TestSingletonMessages.registerConsumer(), getSelf());
  }

  @Override
  public void postStop() {
    if (stoppedBeforeUnregistration) log.warning("Stopped before unregistration");
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            n -> {
              if (n <= current) getContext().stop(self());
              else {
                current = n;
                delegateTo.tell(n, getSelf());
              }
            })
        .match(RegistrationOk.class, message -> delegateTo.tell(message, getSelf()))
        .match(UnexpectedRegistration.class, message -> delegateTo.tell(message, getSelf()))
        .match(GetCurrent.class, message -> getSender().tell(current, getSelf()))
        // #consumer-end
        .match(End.class, message -> queue.tell(UnregisterConsumer.class, getSelf()))
        .match(
            UnregistrationOk.class,
            message -> {
              stoppedBeforeUnregistration = false;
              getContext().stop(getSelf());
            })
        .match(Ping.class, message -> getSender().tell(TestSingletonMessages.pong(), getSelf()))
        // #consumer-end
        .build();
  }
}

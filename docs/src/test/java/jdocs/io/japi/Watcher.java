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

package jdocs.io.japi;

import java.util.concurrent.CountDownLatch;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Terminated;

public class Watcher extends AbstractActor {

  public static class Watch {
    final ActorRef target;

    public Watch(ActorRef target) {
      this.target = target;
    }
  }

  final CountDownLatch latch;

  public Watcher(CountDownLatch latch) {
    this.latch = latch;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Watch.class,
            msg -> {
              getContext().watch(msg.target);
            })
        .match(
            Terminated.class,
            msg -> {
              latch.countDown();
              if (latch.getCount() == 0) getContext().stop(getSelf());
            })
        .build();
  }
}

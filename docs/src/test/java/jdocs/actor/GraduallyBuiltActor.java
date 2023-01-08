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

package jdocs.actor;

// #imports
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.japi.pf.ReceiveBuilder;

// #imports

// #actor
public class GraduallyBuiltActor extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  @Override
  public Receive createReceive() {
    ReceiveBuilder builder = ReceiveBuilder.create();

    builder.match(
        String.class,
        s -> {
          log.info("Received String message: {}", s);
          // #actor
          // #reply
          getSender().tell(s, getSelf());
          // #reply
          // #actor
        });

    // do some other stuff in between

    builder.matchAny(o -> log.info("received unknown message"));

    return builder.build();
  }
}
// #actor

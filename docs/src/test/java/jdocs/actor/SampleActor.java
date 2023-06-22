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

// #sample-actor
import org.apache.pekko.actor.AbstractActor;

public class SampleActor extends AbstractActor {

  private Receive guarded =
      receiveBuilder()
          .match(
              String.class,
              s -> s.contains("guard"),
              s -> {
                getSender().tell("contains(guard): " + s, getSelf());
                getContext().unbecome();
              })
          .build();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Double.class,
            d -> {
              getSender().tell(d.isNaN() ? 0 : d, getSelf());
            })
        .match(
            Integer.class,
            i -> {
              getSender().tell(i * 10, getSelf());
            })
        .match(
            String.class,
            s -> s.startsWith("guard"),
            s -> {
              getSender().tell("startsWith(guard): " + s.toUpperCase(), getSelf());
              getContext().become(guarded, false);
            })
        .build();
  }
}
// #sample-actor

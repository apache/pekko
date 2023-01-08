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

package jdocs.org.apache.pekko.typed.fromclassic;

// #hello-world-actor
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

// #hello-world-actor

interface ClassicSample {

  // #hello-world-actor
  public class HelloWorld extends AbstractActor {

    public static final class Greet {
      public final String whom;

      public Greet(String whom) {
        this.whom = whom;
      }
    }

    public static final class Greeted {
      public final String whom;

      public Greeted(String whom) {
        this.whom = whom;
      }
    }

    public static Props props() {
      return Props.create(HelloWorld.class, HelloWorld::new);
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Override
    public Receive createReceive() {
      return receiveBuilder().match(Greet.class, this::onGreet).build();
    }

    private void onGreet(Greet command) {
      log.info("Hello {}!", command.whom);
      getSender().tell(new Greeted(command.whom), getSelf());
    }
  }
  // #hello-world-actor

}

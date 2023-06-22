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

// #imports1
import java.time.Duration;
// #imports1

// #imports2
import org.apache.pekko.actor.Cancellable;
// #imports2

import jdocs.AbstractJavaTest;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.junit.*;

public class SchedulerDocTest extends AbstractJavaTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("SchedulerDocTest", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();
  private ActorRef testActor = system.actorOf(Props.create(MyActor.class));

  @Test
  public void scheduleOneOffTask() {
    // #schedule-one-off-message
    system
        .scheduler()
        .scheduleOnce(
            Duration.ofMillis(50), testActor, "foo", system.dispatcher(), ActorRef.noSender());
    // #schedule-one-off-message

    // #schedule-one-off-thunk
    system
        .scheduler()
        .scheduleOnce(
            Duration.ofMillis(50),
            new Runnable() {
              @Override
              public void run() {
                testActor.tell(System.currentTimeMillis(), ActorRef.noSender());
              }
            },
            system.dispatcher());
    // #schedule-one-off-thunk
  }

  @Test
  public void scheduleRecurringTask() {
    // #schedule-recurring
    class Ticker extends AbstractActor {
      @Override
      public Receive createReceive() {
        return receiveBuilder()
            .matchEquals(
                "Tick",
                m -> {
                  // Do something
                })
            .build();
      }
    }

    ActorRef tickActor = system.actorOf(Props.create(Ticker.class, this));

    // This will schedule to send the Tick-message
    // to the tickActor after 0ms repeating every 50ms
    Cancellable cancellable =
        system
            .scheduler()
            .scheduleWithFixedDelay(
                Duration.ZERO,
                Duration.ofMillis(50),
                tickActor,
                "Tick",
                system.dispatcher(),
                ActorRef.noSender());

    // This cancels further Ticks to be sent
    cancellable.cancel();
    // #schedule-recurring
    system.stop(tickActor);
  }
}

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.eventstream;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;

public class EventStreamTest {

  static class SomeClass {}

  public static void compileOnlyTest(ActorSystem<?> actorSystem, ActorRef<SomeClass> actorRef) {
    actorSystem.eventStream().tell(new EventStream.Subscribe<>(SomeClass.class, actorRef));
  }
}

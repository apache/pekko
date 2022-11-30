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

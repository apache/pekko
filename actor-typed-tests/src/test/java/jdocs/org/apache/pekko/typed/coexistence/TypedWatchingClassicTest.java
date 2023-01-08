/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.typed.coexistence;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
// #adapter-import
// in Java use the static methods on Adapter to convert from classic to typed
import org.apache.pekko.actor.typed.javadsl.Adapter;
// #adapter-import
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class TypedWatchingClassicTest extends JUnitSuite {

  // #typed
  public static class Typed extends AbstractBehavior<Typed.Command> {

    public static class Ping {
      public final org.apache.pekko.actor.typed.ActorRef<Pong> replyTo;

      public Ping(ActorRef<Pong> replyTo) {
        this.replyTo = replyTo;
      }
    }

    interface Command {}

    public enum Pong implements Command {
      INSTANCE
    }

    private final org.apache.pekko.actor.ActorRef second;

    private Typed(ActorContext<Command> context, org.apache.pekko.actor.ActorRef second) {
      super(context);
      this.second = second;
    }

    public static Behavior<Command> create() {
      return org.apache.pekko.actor.typed.javadsl.Behaviors.setup(
          context -> {
            org.apache.pekko.actor.ActorRef second =
                Adapter.actorOf(context, Classic.props(), "second");

            Adapter.watch(context, second);

            second.tell(
                new Typed.Ping(context.getSelf().narrow()), Adapter.toClassic(context.getSelf()));

            return new Typed(context, second);
          });
    }

    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
          .onMessage(Typed.Pong.class, message -> onPong())
          .onSignal(org.apache.pekko.actor.typed.Terminated.class, sig -> Behaviors.stopped())
          .build();
    }

    private Behavior<Command> onPong() {
      Adapter.stop(getContext(), second);
      return this;
    }
  }
  // #typed

  // #classic
  public static class Classic extends AbstractActor {
    public static org.apache.pekko.actor.Props props() {
      return org.apache.pekko.actor.Props.create(Classic.class);
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder().match(Typed.Ping.class, this::onPing).build();
    }

    private void onPing(Typed.Ping message) {
      message.replyTo.tell(Typed.Pong.INSTANCE);
    }
  }
  // #classic

  @Test
  public void testItWorks() {
    // #create
    ActorSystem as = ActorSystem.create();
    ActorRef<Typed.Command> typed = Adapter.spawn(as, Typed.create(), "Typed");
    // #create
    TestKit probe = new TestKit(as);
    probe.watch(Adapter.toClassic(typed));
    probe.expectTerminated(Adapter.toClassic(typed));
    TestKit.shutdownActorSystem(as);
  }
}

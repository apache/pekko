/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.typed.coexistence;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
// #adapter-import
// In java use the static methods on Adapter to convert from typed to classic
import org.apache.pekko.actor.typed.javadsl.Adapter;
// #adapter-import
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.concurrent.duration.Duration;

import static org.apache.pekko.actor.typed.javadsl.Behaviors.same;

public class ClassicWatchingTypedTest extends JUnitSuite {

  // #classic-watch
  public static class Classic extends AbstractActor {
    public static org.apache.pekko.actor.Props props() {
      return org.apache.pekko.actor.Props.create(Classic.class);
    }

    private final org.apache.pekko.actor.typed.ActorRef<Typed.Command> second =
        Adapter.spawn(getContext(), Typed.behavior(), "second");

    @Override
    public void preStart() {
      Adapter.watch(getContext(), second);
      second.tell(new Typed.Ping(Adapter.toTyped(getSelf())));
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Typed.Pong.class,
              message -> {
                Adapter.stop(getContext(), second);
              })
          .match(
              org.apache.pekko.actor.Terminated.class,
              t -> {
                getContext().stop(getSelf());
              })
          .build();
    }
  }
  // #classic-watch

  // #typed
  public abstract static class Typed {
    interface Command {}

    public static class Ping implements Command {
      public final org.apache.pekko.actor.typed.ActorRef<Pong> replyTo;

      public Ping(ActorRef<Pong> replyTo) {
        this.replyTo = replyTo;
      }
    }

    public static class Pong {}

    public static Behavior<Command> behavior() {
      return Behaviors.receive(Typed.Command.class)
          .onMessage(
              Typed.Ping.class,
              message -> {
                message.replyTo.tell(new Pong());
                return same();
              })
          .build();
    }
  }
  // #typed

  @Test
  public void testItWorks() {
    // #create-classic
    org.apache.pekko.actor.ActorSystem as = org.apache.pekko.actor.ActorSystem.create();
    org.apache.pekko.actor.ActorRef classic = as.actorOf(Classic.props());
    // #create-classic
    TestProbe probe = new TestProbe(as);
    probe.watch(classic);
    probe.expectTerminated(classic, Duration.create(1, "second"));
    TestKit.shutdownActorSystem(as);
  }

  @Test
  public void testConversionFromClassicSystemToTyped() {
    // #convert-classic
    org.apache.pekko.actor.ActorSystem classicActorSystem =
        org.apache.pekko.actor.ActorSystem.create();
    ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
    // #convert-classic
    TestKit.shutdownActorSystem(classicActorSystem);
  }
}

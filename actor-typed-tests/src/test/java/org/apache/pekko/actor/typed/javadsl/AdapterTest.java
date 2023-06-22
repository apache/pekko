/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.typed.internal.adapter.SchedulerAdapter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.actor.SupervisorStrategy;
import static org.apache.pekko.actor.typed.javadsl.Behaviors.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class AdapterTest extends JUnitSuite {

  static org.apache.pekko.actor.Props classic1() {
    return org.apache.pekko.actor.Props.create(Classic1.class, () -> new Classic1());
  }

  static class Classic1 extends org.apache.pekko.actor.AbstractActor {
    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals("ping", s -> getSender().tell("pong", getSelf()))
          .match(
              ThrowIt.class,
              t -> {
                throw t;
              })
          .build();
    }
  }

  static class Typed1 {
    private final org.apache.pekko.actor.ActorRef ref;
    private final org.apache.pekko.actor.ActorRef probe;

    private Typed1(org.apache.pekko.actor.ActorRef ref, org.apache.pekko.actor.ActorRef probe) {
      this.ref = ref;
      this.probe = probe;
    }

    static Behavior<String> create(
        org.apache.pekko.actor.ActorRef ref, org.apache.pekko.actor.ActorRef probe) {
      Typed1 logic = new Typed1(ref, probe);
      return receive(logic::onMessage, logic::onSignal);
    }

    Behavior<String> onMessage(ActorContext<String> context, String message) {
      if (message.equals("send")) {
        org.apache.pekko.actor.ActorRef replyTo = Adapter.toClassic(context.getSelf());
        ref.tell("ping", replyTo);
        return same();
      } else if (message.equals("pong")) {
        probe.tell("ok", org.apache.pekko.actor.ActorRef.noSender());
        return same();
      } else if (message.equals("actorOf")) {
        org.apache.pekko.actor.ActorRef child = Adapter.actorOf(context, classic1());
        child.tell("ping", Adapter.toClassic(context.getSelf()));
        return same();
      } else if (message.equals("watch")) {
        Adapter.watch(context, ref);
        return same();
      } else if (message.equals("supervise-restart")) {
        // restart is the default, otherwise an intermediate is required
        org.apache.pekko.actor.ActorRef child = Adapter.actorOf(context, classic1());
        Adapter.watch(context, child);
        child.tell(new ThrowIt3(), Adapter.toClassic(context.getSelf()));
        child.tell("ping", Adapter.toClassic(context.getSelf()));
        return same();
      } else if (message.equals("stop-child")) {
        org.apache.pekko.actor.ActorRef child = Adapter.actorOf(context, classic1());
        Adapter.watch(context, child);
        Adapter.stop(context, child);
        return same();
      } else if (message.equals("stop-self")) {
        try {
          context.stop(context.getSelf());
        } catch (Exception e) {
          probe.tell(e, org.apache.pekko.actor.ActorRef.noSender());
        }
        return same();
      } else {
        return unhandled();
      }
    }

    Behavior<String> onSignal(ActorContext<String> context, Signal sig) {
      if (sig instanceof Terminated) {
        probe.tell("terminated", org.apache.pekko.actor.ActorRef.noSender());
        return same();
      } else {
        return unhandled();
      }
    }
  }

  static interface Typed2Msg {};

  static final class Ping implements Typed2Msg {
    public final ActorRef<String> replyTo;

    public Ping(ActorRef<String> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static final class StopIt implements Typed2Msg {}

  abstract static class ThrowIt extends RuntimeException implements Typed2Msg {}

  static class ThrowIt1 extends ThrowIt {}

  static class ThrowIt2 extends ThrowIt {}

  static class ThrowIt3 extends ThrowIt {}

  static org.apache.pekko.actor.Props classic2(
      ActorRef<Ping> ref, org.apache.pekko.actor.ActorRef probe) {
    return org.apache.pekko.actor.Props.create(Classic2.class, () -> new Classic2(ref, probe));
  }

  static class Classic2 extends org.apache.pekko.actor.AbstractActor {
    private final ActorRef<Ping> ref;
    private final org.apache.pekko.actor.ActorRef probe;
    private final SupervisorStrategy strategy;

    Classic2(ActorRef<Ping> ref, org.apache.pekko.actor.ActorRef probe) {
      this.ref = ref;
      this.probe = probe;
      this.strategy = strategy();
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals(
              "send",
              s -> {
                ActorRef<String> replyTo = Adapter.toTyped(getSelf());
                ref.tell(new Ping(replyTo));
              })
          .matchEquals("pong", s -> probe.tell("ok", getSelf()))
          .matchEquals(
              "spawn",
              s -> {
                ActorRef<Typed2Msg> child = Adapter.spawnAnonymous(getContext(), typed2());
                child.tell(new Ping(Adapter.toTyped(getSelf())));
              })
          .matchEquals(
              "actorOf-props",
              s -> {
                // this is how Cluster Sharding can be used
                org.apache.pekko.actor.ActorRef child = getContext().actorOf(typed2Props());
                child.tell(
                    new Ping(Adapter.toTyped(getSelf())),
                    org.apache.pekko.actor.ActorRef.noSender());
              })
          .matchEquals("watch", s -> Adapter.watch(getContext(), ref))
          .match(org.apache.pekko.actor.Terminated.class, t -> probe.tell("terminated", getSelf()))
          .matchEquals("supervise-stop", s -> testSupervice(new ThrowIt1()))
          .matchEquals("supervise-resume", s -> testSupervice(new ThrowIt2()))
          .matchEquals("supervise-restart", s -> testSupervice(new ThrowIt3()))
          .matchEquals(
              "stop-child",
              s -> {
                ActorRef<Typed2Msg> child = Adapter.spawnAnonymous(getContext(), typed2());
                Adapter.watch(getContext(), child);
                Adapter.stop(getContext(), child);
              })
          .build();
    }

    private void testSupervice(ThrowIt t) {
      ActorRef<Typed2Msg> child = Adapter.spawnAnonymous(getContext(), typed2());
      Adapter.watch(getContext(), child);
      child.tell(t);
      child.tell(new Ping(Adapter.toTyped(getSelf())));
    }

    private SupervisorStrategy strategy() {
      return new org.apache.pekko.actor.OneForOneStrategy(
          false,
          org.apache.pekko.japi.pf.DeciderBuilder.match(
                  ThrowIt1.class,
                  e -> {
                    probe.tell("thrown-stop", getSelf());
                    return SupervisorStrategy.stop();
                  })
              .match(
                  ThrowIt2.class,
                  e -> {
                    probe.tell("thrown-resume", getSelf());
                    return SupervisorStrategy.resume();
                  })
              .match(
                  ThrowIt3.class,
                  e -> {
                    probe.tell("thrown-restart", getSelf());
                    // TODO Restart will not really restart the behavior
                    return SupervisorStrategy.restart();
                  })
              .build());
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
      return strategy;
    }
  }

  static Behavior<Typed2Msg> typed2() {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof Ping) {
            ActorRef<String> replyTo = ((Ping) message).replyTo;
            replyTo.tell("pong");
            return same();
          } else if (message instanceof StopIt) {
            return stopped();
          } else if (message instanceof ThrowIt) {
            throw (ThrowIt) message;
          } else {
            return unhandled();
          }
        });
  }

  static org.apache.pekko.actor.Props typed2Props() {
    return Adapter.props(() -> typed2());
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("ActorSelectionTest", PekkoSpec.testConf());

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  private final ActorSystem system = actorSystemResource.getSystem();

  @Test
  public void shouldSendMessageFromTypedToClassic() {
    TestKit probe = new TestKit(system);
    org.apache.pekko.actor.ActorRef classicRef = system.actorOf(classic1());
    ActorRef<String> typedRef =
        Adapter.spawnAnonymous(system, Typed1.create(classicRef, probe.getRef()));
    typedRef.tell("send");
    probe.expectMsg("ok");
  }

  @Test
  public void shouldSendMessageFromClassicToTyped() {
    TestKit probe = new TestKit(system);
    ActorRef<Ping> typedRef = Adapter.spawnAnonymous(system, typed2()).narrow();
    org.apache.pekko.actor.ActorRef classicRef = system.actorOf(classic2(typedRef, probe.getRef()));
    classicRef.tell("send", org.apache.pekko.actor.ActorRef.noSender());
    probe.expectMsg("ok");
  }

  @Test
  public void shouldSpawnTypedChildFromClassicParent() {
    TestKit probe = new TestKit(system);
    ActorRef<Ping> ignore = Adapter.spawnAnonymous(system, ignore());
    org.apache.pekko.actor.ActorRef classicRef = system.actorOf(classic2(ignore, probe.getRef()));
    classicRef.tell("spawn", org.apache.pekko.actor.ActorRef.noSender());
    probe.expectMsg("ok");
  }

  @Test
  public void shouldActorOfTypedChildViaPropsFromClassicParent() {
    TestKit probe = new TestKit(system);
    ActorRef<Ping> ignore = Adapter.spawnAnonymous(system, ignore());
    org.apache.pekko.actor.ActorRef classicRef = system.actorOf(classic2(ignore, probe.getRef()));
    classicRef.tell("actorOf-props", org.apache.pekko.actor.ActorRef.noSender());
    probe.expectMsg("ok");
  }

  @Test
  public void shouldActorOfClassicChildFromTypedParent() {
    TestKit probe = new TestKit(system);
    org.apache.pekko.actor.ActorRef ignore = system.actorOf(org.apache.pekko.actor.Props.empty());
    ActorRef<String> typedRef =
        Adapter.spawnAnonymous(system, Typed1.create(ignore, probe.getRef()));
    typedRef.tell("actorOf");
    probe.expectMsg("ok");
  }

  @Test
  public void shouldWatchTypedFromClassic() {
    TestKit probe = new TestKit(system);
    ActorRef<Typed2Msg> typedRef = Adapter.spawnAnonymous(system, typed2());
    ActorRef<Ping> typedRef2 = typedRef.narrow();
    org.apache.pekko.actor.ActorRef classicRef =
        system.actorOf(classic2(typedRef2, probe.getRef()));
    classicRef.tell("watch", org.apache.pekko.actor.ActorRef.noSender());
    typedRef.tell(new StopIt());
    probe.expectMsg("terminated");
  }

  @Test
  public void shouldWatchClassicFromTyped() {
    TestKit probe = new TestKit(system);
    org.apache.pekko.actor.ActorRef classicRef = system.actorOf(classic1());
    ActorRef<String> typedRef =
        Adapter.spawnAnonymous(system, Typed1.create(classicRef, probe.getRef()));
    typedRef.tell("watch");
    classicRef.tell(
        org.apache.pekko.actor.PoisonPill.getInstance(),
        org.apache.pekko.actor.ActorRef.noSender());
    probe.expectMsg("terminated");
  }

  @Test
  public void shouldSuperviseClassicChildAsRestartFromTypedParent() {
    TestKit probe = new TestKit(system);
    org.apache.pekko.actor.ActorRef ignore = system.actorOf(org.apache.pekko.actor.Props.empty());
    ActorRef<String> typedRef =
        Adapter.spawnAnonymous(system, Typed1.create(ignore, probe.getRef()));

    int originalLogLevel = system.getEventStream().logLevel();
    try {
      // suppress the logging with stack trace
      system.getEventStream().setLogLevel(Integer.MIN_VALUE); // OFF

      typedRef.tell("supervise-restart");
      probe.expectMsg("ok");
    } finally {
      system.getEventStream().setLogLevel(originalLogLevel);
    }
    probe.expectNoMessage(Duration.ofMillis(100)); // no pong
  }

  @Test
  public void shouldStopTypedChildFromClassicParent() {
    TestKit probe = new TestKit(system);
    ActorRef<Ping> ignore = Adapter.spawnAnonymous(system, ignore());
    org.apache.pekko.actor.ActorRef classicRef = system.actorOf(classic2(ignore, probe.getRef()));
    classicRef.tell("stop-child", org.apache.pekko.actor.ActorRef.noSender());
    probe.expectMsg("terminated");
  }

  @Test
  public void shouldStopClassicChildFromTypedParent() {
    TestKit probe = new TestKit(system);
    org.apache.pekko.actor.ActorRef ignore = system.actorOf(org.apache.pekko.actor.Props.empty());
    ActorRef<String> typedRef =
        Adapter.spawnAnonymous(system, Typed1.create(ignore, probe.getRef()));
    typedRef.tell("stop-child");
    probe.expectMsg("terminated");
  }

  @Test
  public void stopSelfWillCauseError() {
    TestKit probe = new TestKit(system);
    org.apache.pekko.actor.ActorRef ignore = system.actorOf(org.apache.pekko.actor.Props.empty());
    ActorRef<String> typedRef =
        Adapter.spawnAnonymous(system, Typed1.create(ignore, probe.getRef()));
    typedRef.tell("stop-self");
    probe.expectMsgClass(IllegalArgumentException.class);
  }

  @Test
  public void shouldConvertScheduler() {
    org.apache.pekko.actor.typed.Scheduler typedScheduler = Adapter.toTyped(system.scheduler());
    assertEquals(SchedulerAdapter.class, typedScheduler.getClass());
    org.apache.pekko.actor.Scheduler classicScheduler = Adapter.toClassic(typedScheduler);
    assertSame(system.scheduler(), classicScheduler);
  }
}

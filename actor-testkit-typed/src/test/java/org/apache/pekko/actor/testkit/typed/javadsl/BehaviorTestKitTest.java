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

package org.apache.pekko.actor.testkit.typed.javadsl;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.CapturedLogEvent;
import org.apache.pekko.actor.testkit.typed.Effect;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

public class BehaviorTestKitTest {

  public interface Command {}

  public record SpawnWatchAndUnWatch(String name) implements Command {}

  public record SpawnAndWatchWith(String name) implements Command {}

  public record SpawnSession(ActorRef<ActorRef<String>> replyTo, ActorRef<String> sessionHandler)
      implements Command {}

  public record KillSession(ActorRef<String> session, ActorRef<Done> replyTo) implements Command {}

  @SuppressWarnings("unchecked")
  public record CreateMessageAdapter(
      Class<Object> clazz, org.apache.pekko.japi.function.Function<Object, Command> f)
      implements Command {}

  public record SpawnChildren(int numberOfChildren) implements Command {}

  public record SpawnChildrenAnonymous(int numberOfChildren) implements Command {}

  public record SpawnChildrenWithProps(int numberOfChildren, Props props) implements Command {}

  public record SpawnChildrenAnonymousWithProps(int numberOfChildren, Props props)
      implements Command {}

  public record Log(String what) implements Command {}

  public record AskForCookiesFrom(ActorRef<CookieDistributorCommand> distributor)
      implements Command {}

  public record ScheduleOnceCommand(
      Duration delay,
      ActorRef<String> target,
      String message,
      ActorRef<org.apache.pekko.actor.Cancellable> replyTo)
      implements Command {}

  public interface CookieDistributorCommand {}

  public record GiveMeCookies(int nrCookies, ActorRef<CookiesForYou> replyTo)
      implements CookieDistributorCommand {}

  public record CookiesForYou(int nrCookies) {}

  public interface Action {}

  private static Behavior<Action> childInitial = Behaviors.ignore();

  private static Props props = Props.empty().withDispatcherFromConfig("cat");

  private static Behavior<Command> behavior =
      Behaviors.setup(
          context -> {
            return Behaviors.receive(Command.class)
                .onMessage(
                    SpawnChildren.class,
                    message -> {
                      IntStream.range(0, message.numberOfChildren())
                          .forEach(
                              i -> {
                                context.spawn(childInitial, "child" + i);
                              });
                      return Behaviors.same();
                    })
                .onMessage(
                    SpawnChildrenAnonymous.class,
                    message -> {
                      IntStream.range(0, message.numberOfChildren())
                          .forEach(
                              i -> {
                                context.spawnAnonymous(childInitial);
                              });
                      return Behaviors.same();
                    })
                .onMessage(
                    SpawnChildrenWithProps.class,
                    message -> {
                      IntStream.range(0, message.numberOfChildren())
                          .forEach(
                              i -> {
                                context.spawn(childInitial, "child" + i, message.props());
                              });
                      return Behaviors.same();
                    })
                .onMessage(
                    SpawnChildrenAnonymousWithProps.class,
                    message -> {
                      IntStream.range(0, message.numberOfChildren())
                          .forEach(
                              i -> {
                                context.spawnAnonymous(childInitial, message.props());
                              });
                      return Behaviors.same();
                    })
                .onMessage(
                    CreateMessageAdapter.class,
                    message -> {
                      context.messageAdapter(message.clazz(), message.f());
                      return Behaviors.same();
                    })
                .onMessage(
                    SpawnWatchAndUnWatch.class,
                    message -> {
                      ActorRef<Action> c = context.spawn(childInitial, message.name());
                      context.watch(c);
                      context.unwatch(c);
                      return Behaviors.same();
                    })
                .onMessage(
                    SpawnAndWatchWith.class,
                    message -> {
                      ActorRef<Action> c = context.spawn(childInitial, message.name());
                      context.watchWith(c, message);
                      return Behaviors.same();
                    })
                .onMessage(
                    SpawnSession.class,
                    message -> {
                      ActorRef<String> session =
                          context.spawnAnonymous(
                              Behaviors.receiveMessage(
                                  m -> {
                                    message.sessionHandler().tell(m);
                                    return Behaviors.same();
                                  }));
                      message.replyTo().tell(session);
                      return Behaviors.same();
                    })
                .onMessage(
                    KillSession.class,
                    message -> {
                      context.stop(message.session());
                      message.replyTo().tell(Done.getInstance());
                      return Behaviors.same();
                    })
                .onMessage(
                    Log.class,
                    message -> {
                      context.getLog().info(message.what());
                      return Behaviors.same();
                    })
                .onMessage(
                    AskForCookiesFrom.class,
                    message -> {
                      context.ask(
                          CookiesForYou.class,
                          message.distributor(),
                          Duration.ofSeconds(10),
                          (ActorRef<CookiesForYou> ref) -> new GiveMeCookies(6, ref),
                          (response, throwable) -> {
                            if (response != null) {
                              return new Log(
                                  "Got " + response.nrCookies() + " cookies from distributor");
                            } else {
                              return new Log("Failed to get cookies: " + throwable.getMessage());
                            }
                          });
                      return Behaviors.same();
                    })
                .onMessage(
                    ScheduleOnceCommand.class,
                    message -> {
                      org.apache.pekko.actor.Cancellable cancellable =
                          context.scheduleOnce(
                              message.delay(), message.target(), message.message());
                      message.replyTo().tell(cancellable);
                      return Behaviors.same();
                    })
                .build();
          });

  @Test
  public void allowAssertionsOnEffectType() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.run(new SpawnChildren(1));
    Effect.Spawned spawned = test.expectEffectClass(Effect.Spawned.class);
    assertEquals("child0", spawned.childName());
  }

  @Test
  public void allowExpectingNoEffects() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.expectEffect(Effects.noEffects());
  }

  @Test
  public void allowsExpectingNoEffectByType() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.expectEffectClass(Effect.NoEffects.class);
  }

  @Test
  public void allowRetrieveAllLogs() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    String what = "Hello!";
    test.run(new Log(what));
    final List<CapturedLogEvent> allLogEntries = test.getAllLogEntries();
    assertEquals(1, allLogEntries.size());
    assertEquals(new CapturedLogEvent(Level.INFO, what), allLogEntries.get(0));
  }

  @Test
  public void allowClearLogs() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    String what = "Hello!";
    test.run(new Log(what));
    assertEquals(1, test.getAllLogEntries().size());
    test.clearLog();
    assertEquals(0, test.getAllLogEntries().size());
  }

  @Test
  public void returnEffectsThatHaveTakenPlace() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    assertFalse(test.hasEffects());
    test.run(new SpawnChildrenAnonymous(1));
    assertTrue(test.hasEffects());
  }

  @Test
  @Disabled("Not supported for Java API")
  public void allowAssertionsUsingPartialFunctions() {}

  @Test
  public void spawnChildrenWithNoProps() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.run(new SpawnChildren(2));
    List<Effect> allEffects = test.getAllEffects();
    assertEquals(
        List.of(
            Effects.spawned(childInitial, "child0"),
            Effects.spawned(childInitial, "child1", Props.empty())),
        allEffects);
  }

  @Test
  public void spawnChildrenWithProps() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.run(new SpawnChildrenWithProps(1, props));
    assertEquals(props, test.expectEffectClass(Effect.Spawned.class).props());
  }

  @Test
  public void spawnAnonChildrenWithNoProps() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.run(new SpawnChildrenAnonymous(2));
    List<Effect> allEffects = test.getAllEffects();
    assertEquals(
        List.of(
            Effects.spawnedAnonymous(childInitial),
            Effects.spawnedAnonymous(childInitial, Props.empty())),
        allEffects);
  }

  @Test
  public void spawnAnonChildrenWithProps() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.run(new SpawnChildrenAnonymousWithProps(1, props));
    assertEquals(props, test.expectEffectClass(Effect.SpawnedAnonymous.class).props());
  }

  @Test
  public void createMessageAdapters() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    SpawnChildren adaptedMessage = new SpawnChildren(1);
    @SuppressWarnings("unchecked")
    Class<Object> stringClass = (Class) String.class;
    test.run(new CreateMessageAdapter(stringClass, o -> adaptedMessage));
    @SuppressWarnings("unchecked")
    Effect.MessageAdapter<String, SpawnChildren> mAdapter =
        test.<Effect.MessageAdapter>expectEffectClass(Effect.MessageAdapter.class);
    assertEquals(String.class, mAdapter.messageClass());
    assertEquals(adaptedMessage, mAdapter.adaptFunction().apply("anything"));
  }

  @Test
  public void recordWatching() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    test.run(new SpawnWatchAndUnWatch("name"));
    ActorRef<Object> child = test.childInbox("name").getRef();
    test.expectEffectClass(Effect.Spawned.class);
    assertEquals(child, test.expectEffectClass(Effect.Watched.class).other());
    assertEquals(child, test.expectEffectClass(Effect.Unwatched.class).other());
  }

  @Test
  public void recordWatchWith() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    SpawnAndWatchWith spawnAndWatchWithMsg = new SpawnAndWatchWith("name");
    test.run(spawnAndWatchWithMsg);
    ActorRef<Object> child = test.childInbox("name").getRef();
    test.expectEffectClass(Effect.Spawned.class);

    Effect.WatchedWith watchedWith = test.expectEffectClass(Effect.WatchedWith.class);
    assertEquals(child, watchedWith.other());
    assertEquals(spawnAndWatchWithMsg, watchedWith.message());
  }

  @Test
  public void allowRetrievingAndKilling() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    TestInbox<String> h = TestInbox.create();

    ReplyInbox<ActorRef<String>> sessionReply =
        test.runAsk(replyTo -> new SpawnSession(replyTo, h.getRef()));

    ActorRef<String> sessionRef = sessionReply.receiveReply();

    Effect.SpawnedAnonymous s = test.expectEffectClass(Effect.SpawnedAnonymous.class);
    assertEquals(sessionRef, s.ref());

    BehaviorTestKit<String> session = test.childTestKit(sessionRef);
    session.run("hello");
    assertEquals(List.of("hello"), h.getAllReceived());

    ReplyInbox<Done> doneReply = test.runAsk(replyTo -> new KillSession(sessionRef, replyTo));
    doneReply.expectReply(Done.getInstance());

    test.expectEffectClass(Effect.Stopped.class);
  }

  @Test
  public void canUseTimerScheduledInJavaApi() {
    // this is a compilation test
    Effect.TimerScheduled<String> timerScheduled =
        Effects.timerScheduled(
            "my key",
            "my msg",
            Duration.ofSeconds(42),
            Effect.timerScheduled().fixedDelayMode(),
            false,
            () -> {});
    assertNotNull(timerScheduled);
  }

  @Test
  public void reifyAskAsEffect() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    TestInbox<CookieDistributorCommand> cdInbox = TestInbox.create();

    test.run(new AskForCookiesFrom(cdInbox.getRef()));

    Effect expectedEffect =
        Effects.askInitiated(
            cdInbox.getRef(), Duration.ofSeconds(10), CookiesForYou.class, Command.class);
    Effect.AskInitiated<?, ?, ?> actualEffect = test.expectEffectClass(Effect.AskInitiated.class);

    assertEquals(actualEffect, expectedEffect);

    // Other functionality is tested in the scaladsl
  }

  @Test
  public void scheduleOnceReturnsNonCancelledCancellable() {
    BehaviorTestKit<Command> test = BehaviorTestKit.create(behavior);
    TestInbox<String> target = TestInbox.create("target");
    TestInbox<org.apache.pekko.actor.Cancellable> replyTo = TestInbox.create("cancellable");

    test.run(
        new ScheduleOnceCommand(
            Duration.ofSeconds(42), target.getRef(), "hello", replyTo.getRef()));

    test.expectEffectClass(Effect.Scheduled.class);

    org.apache.pekko.actor.Cancellable cancellable = replyTo.receiveMessage();
    assertFalse(cancellable.isCancelled(), "Freshly scheduled Cancellable should not be cancelled");

    assertTrue(cancellable.cancel(), "First cancel() should return true");
    assertTrue(cancellable.isCancelled(), "isCancelled should be true after cancel()");

    assertFalse(cancellable.cancel(), "Second cancel() should return false");
    assertTrue(cancellable.isCancelled(), "isCancelled should remain true");
  }
}

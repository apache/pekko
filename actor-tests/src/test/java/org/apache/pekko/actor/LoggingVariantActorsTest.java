/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.actor;

import java.time.Duration;

import scala.concurrent.duration.FiniteDuration;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.event.Logging;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.EventFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.*;

public class LoggingVariantActorsTest {

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource(
          "LoggingVariantActorsTest",
          ConfigFactory.parseString("pekko.loggers = [org.apache.pekko.testkit.TestEventListener]")
              .withFallback(ActorWithBoundedStashSpec.testConf()));

  private final ActorSystem system = actorSystemResource.getSystem();

  // --- stash actor implementations ---

  public static class WithStashAndLogging extends AbstractLoggingActorWithStash {
    int count = 0;

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals(
              "fail",
              s -> {
                throw new RuntimeException("restart requested");
              })
          .match(
              String.class,
              s -> {
                if (count < 0) {
                  log().info("Replying with length of: {}", s);
                  getSender().tell(s.length(), getSelf());
                } else if (count == 2) {
                  unstashAll();
                  count = -1;
                } else {
                  stash();
                  count += 1;
                }
              })
          .build();
    }
  }

  public static class UntypedWithStashAndLogging extends UntypedAbstractLoggingActorWithStash {
    int count = 0;

    @Override
    public void onReceive(Object msg) throws Exception {
      if ("fail".equals(msg)) {
        throw new RuntimeException("restart requested");
      } else if (msg instanceof String s) {
        if (count < 0) {
          log().info("Replying with length of: {}", s);
          getSender().tell(s.length(), getSelf());
        } else if (count == 2) {
          unstashAll();
          count = -1;
        } else {
          stash();
          count += 1;
        }
      } else {
        unhandled(msg);
      }
    }
  }

  public static class WithUnboundedStashAndLogging extends AbstractLoggingActorWithUnboundedStash {
    int count = 0;

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              String.class,
              s -> {
                if (count < 0) {
                  log().info("Replying with length of: {}", s);
                  getSender().tell(s.length(), getSelf());
                } else if (count == 2) {
                  unstashAll();
                  count = -1;
                } else {
                  stash();
                  count += 1;
                }
              })
          .build();
    }
  }

  public static class UntypedWithUnboundedStashAndLogging
      extends UntypedAbstractLoggingActorWithUnboundedStash {
    int count = 0;

    @Override
    public void onReceive(Object msg) throws Exception {
      if (msg instanceof String s) {
        if (count < 0) {
          log().info("Replying with length of: {}", s);
          getSender().tell(s.length(), getSelf());
        } else if (count == 2) {
          unstashAll();
          count = -1;
        } else {
          stash();
          count += 1;
        }
      } else {
        unhandled(msg);
      }
    }
  }

  public static class WithUnrestrictedStashAndLogging
      extends AbstractLoggingActorWithUnrestrictedStash {
    int count = 0;

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              String.class,
              s -> {
                if (count < 0) {
                  log().info("Replying with length of: {}", s);
                  getSender().tell(s.length(), getSelf());
                } else if (count == 2) {
                  unstashAll();
                  count = -1;
                } else {
                  stash();
                  count += 1;
                }
              })
          .build();
    }
  }

  public static class UntypedWithUnrestrictedStashAndLogging
      extends UntypedAbstractLoggingActorWithUnrestrictedStash {
    int count = 0;

    @Override
    public void onReceive(Object msg) throws Exception {
      if (msg instanceof String s) {
        if (count < 0) {
          log().info("Replying with length of: {}", s);
          getSender().tell(s.length(), getSelf());
        } else if (count == 2) {
          unstashAll();
          count = -1;
        } else {
          stash();
          count += 1;
        }
      } else {
        unhandled(msg);
      }
    }
  }

  // --- timer actor implementations ---

  public static class WithTimersAndLogging extends AbstractLoggingActorWithTimers {
    private ActorRef replyTo;

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals(
              "tick",
              s -> {
                log().info("Timer fired");
                replyTo.tell("done", getSelf());
              })
          .matchEquals(
              "cancel",
              s -> {
                log().info("Cancelling timer");
                getTimers().cancel("key");
                replyTo.tell("cancelled", getSelf());
              })
          .match(
              String.class,
              s -> {
                log().info("Received: {}", s);
                replyTo = getSender();
                getTimers().startSingleTimer("key", "tick", Duration.ofMillis(200));
              })
          .build();
    }
  }

  public static class UntypedWithTimersAndLogging extends UntypedAbstractLoggingActorWithTimers {
    private ActorRef replyTo;

    @Override
    public void onReceive(Object msg) throws Exception {
      if ("tick".equals(msg)) {
        log().info("Timer fired");
        replyTo.tell("done", getSelf());
      } else if ("cancel".equals(msg)) {
        log().info("Cancelling timer");
        getTimers().cancel("key");
        replyTo.tell("cancelled", getSelf());
      } else if (msg instanceof String s) {
        log().info("Received: {}", s);
        replyTo = getSender();
        getTimers().startSingleTimer("key", "tick", Duration.ofMillis(200));
      } else {
        unhandled(msg);
      }
    }
  }

  // --- stash functional tests ---

  private void testStashFunctionality(Props props) {
    ActorRef ref = system.actorOf(props);
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "Hello");
    probe.send(ref, "Hello2");
    probe.send(ref, "Hello12");
    probe.expectMsg(5);
    probe.expectMsg(6);
  }

  @Test
  public void mustSupportStashWithLogging() {
    testStashFunctionality(Props.create(WithStashAndLogging.class));
  }

  @Test
  public void mustSupportUntypedStashWithLogging() {
    testStashFunctionality(Props.create(UntypedWithStashAndLogging.class));
  }

  @Test
  public void mustSupportUnboundedStashWithLogging() {
    testStashFunctionality(Props.create(WithUnboundedStashAndLogging.class));
  }

  @Test
  public void mustSupportUntypedUnboundedStashWithLogging() {
    testStashFunctionality(Props.create(UntypedWithUnboundedStashAndLogging.class));
  }

  @Test
  public void mustSupportUnrestrictedStashWithLogging() {
    testStashFunctionality(
        Props.create(WithUnrestrictedStashAndLogging.class)
            .withMailbox("pekko.actor.mailbox.unbounded-deque-based"));
  }

  @Test
  public void mustSupportUntypedUnrestrictedStashWithLogging() {
    testStashFunctionality(
        Props.create(UntypedWithUnrestrictedStashAndLogging.class)
            .withMailbox("pekko.actor.mailbox.unbounded-deque-based"));
  }

  // --- timer functional tests ---

  @Test
  public void mustSupportTimersWithLogging() {
    ActorRef ref = system.actorOf(Props.create(WithTimersAndLogging.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "start");
    probe.expectMsg(FiniteDuration.create(3, "seconds"), "done");
  }

  @Test
  public void mustSupportUntypedTimersWithLogging() {
    ActorRef ref = system.actorOf(Props.create(UntypedWithTimersAndLogging.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "start");
    probe.expectMsg(FiniteDuration.create(3, "seconds"), "done");
  }

  @Test
  public void mustCancelTimerWithLogging() {
    ActorRef ref = system.actorOf(Props.create(WithTimersAndLogging.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "start");
    probe.send(ref, "cancel");
    probe.expectMsg(FiniteDuration.create(3, "seconds"), "cancelled");
    probe.expectNoMessage(FiniteDuration.create(500, "millis"));
  }

  @Test
  public void mustCancelUntypedTimerWithLogging() {
    ActorRef ref = system.actorOf(Props.create(UntypedWithTimersAndLogging.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "start");
    probe.send(ref, "cancel");
    probe.expectMsg(FiniteDuration.create(3, "seconds"), "cancelled");
    probe.expectNoMessage(FiniteDuration.create(500, "millis"));
  }

  // --- logging event assertions for all variants ---

  @Test
  public void mustEmitLogEventsFromStashVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Replying with length of")
        .occurrences(2)
        .intercept(
            () -> {
              testStashFunctionality(Props.create(WithStashAndLogging.class));
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromUntypedStashVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Replying with length of")
        .occurrences(2)
        .intercept(
            () -> {
              testStashFunctionality(Props.create(UntypedWithStashAndLogging.class));
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromUnboundedStashVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Replying with length of")
        .occurrences(2)
        .intercept(
            () -> {
              testStashFunctionality(Props.create(WithUnboundedStashAndLogging.class));
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromUntypedUnboundedStashVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Replying with length of")
        .occurrences(2)
        .intercept(
            () -> {
              testStashFunctionality(Props.create(UntypedWithUnboundedStashAndLogging.class));
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromUnrestrictedStashVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Replying with length of")
        .occurrences(2)
        .intercept(
            () -> {
              testStashFunctionality(
                  Props.create(WithUnrestrictedStashAndLogging.class)
                      .withMailbox("pekko.actor.mailbox.unbounded-deque-based"));
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromUntypedUnrestrictedStashVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Replying with length of")
        .occurrences(2)
        .intercept(
            () -> {
              testStashFunctionality(
                  Props.create(UntypedWithUnrestrictedStashAndLogging.class)
                      .withMailbox("pekko.actor.mailbox.unbounded-deque-based"));
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromTimerVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Timer fired")
        .occurrences(1)
        .intercept(
            () -> {
              ActorRef ref = system.actorOf(Props.create(WithTimersAndLogging.class));
              final TestProbe probe = new TestProbe(system);
              probe.send(ref, "start");
              probe.expectMsg(FiniteDuration.create(3, "seconds"), "done");
              return null;
            });
  }

  @Test
  public void mustEmitLogEventsFromUntypedTimerVariant() {
    new EventFilter(Logging.Info.class, system)
        .startsWith("Timer fired")
        .occurrences(1)
        .intercept(
            () -> {
              ActorRef ref = system.actorOf(Props.create(UntypedWithTimersAndLogging.class));
              final TestProbe probe = new TestProbe(system);
              probe.send(ref, "start");
              probe.expectMsg(FiniteDuration.create(3, "seconds"), "done");
              return null;
            });
  }

  // --- stash restart behavior (unstash on preRestart) ---

  @Test
  public void mustUnstashOnRestartForStashVariant() {
    ActorRef ref = system.actorOf(Props.create(WithStashAndLogging.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "Hello");
    probe.send(ref, "fail");
    probe.send(ref, "Hello2");
    probe.send(ref, "Hello12");
    probe.expectMsg(FiniteDuration.create(10, "seconds"), 5);
  }

  @Test
  public void mustUnstashOnRestartForUntypedStashVariant() {
    ActorRef ref = system.actorOf(Props.create(UntypedWithStashAndLogging.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "Hello");
    probe.send(ref, "fail");
    probe.send(ref, "Hello2");
    probe.send(ref, "Hello12");
    probe.expectMsg(FiniteDuration.create(10, "seconds"), 5);
  }

  // --- log adapter accessibility ---

  public static class LogAccessorCheck extends AbstractLoggingActorWithStash {
    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals(
              "check",
              s -> {
                assertNotNull(log());
                assertTrue(log().isInfoEnabled());
                getSender().tell("ok", getSelf());
              })
          .build();
    }
  }

  public static class UntypedLogAccessorCheck extends UntypedAbstractLoggingActorWithTimers {
    @Override
    public void onReceive(Object msg) throws Exception {
      if ("check".equals(msg)) {
        assertNotNull(log());
        assertNotNull(getTimers());
        assertTrue(log().isInfoEnabled());
        getSender().tell("ok", getSelf());
      } else {
        unhandled(msg);
      }
    }
  }

  @Test
  public void mustProvideLogAccessor() {
    ActorRef ref = system.actorOf(Props.create(LogAccessorCheck.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "check");
    probe.expectMsg(FiniteDuration.create(3, "seconds"), "ok");
  }

  @Test
  public void mustProvideLogAndTimersAccessors() {
    ActorRef ref = system.actorOf(Props.create(UntypedLogAccessorCheck.class));
    final TestProbe probe = new TestProbe(system);
    probe.send(ref, "check");
    probe.expectMsg(FiniteDuration.create(3, "seconds"), "ok");
  }
}

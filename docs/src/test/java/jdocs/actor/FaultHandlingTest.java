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

import org.apache.pekko.actor.*;

import org.apache.pekko.testkit.javadsl.TestKit;
import org.ekrich.config.Config;
import org.ekrich.config.ConfigFactory;
import jdocs.AbstractJavaTest;
import java.util.Optional;
import java.time.Duration;

import static org.apache.pekko.pattern.Patterns.ask;

// #testkit
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.ErrorFilter;
import org.apache.pekko.testkit.EventFilter;
import org.apache.pekko.testkit.TestEvent;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.pekko.japi.Util.immutableSeq;
import static org.junit.Assert.assertEquals;

import scala.concurrent.Await;

// #testkit

// #supervisor
import org.apache.pekko.japi.pf.DeciderBuilder;
import org.apache.pekko.actor.SupervisorStrategy;

// #supervisor

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

// #testkit
public class FaultHandlingTest extends AbstractJavaTest {
  // #testkit

  public static Config config =
      ConfigFactory.parseString(
          "pekko {\n"
              + "  loggers = [\"org.apache.pekko.testkit.TestEventListener\"]\n"
              + "  loglevel = \"WARNING\"\n"
              + "  stdout-loglevel = \"WARNING\"\n"
              + "}\n");

  public
  // #supervisor
  static class Supervisor extends AbstractActor {

    // #strategy
    private static SupervisorStrategy strategy =
        new OneForOneStrategy(
            10,
            Duration.ofMinutes(1),
            DeciderBuilder.match(ArithmeticException.class, e -> SupervisorStrategy.resume())
                .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
                .matchAny(o -> SupervisorStrategy.escalate())
                .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
      return strategy;
    }

    // #strategy

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Props.class,
              props -> {
                getSender().tell(getContext().actorOf(props), getSelf());
              })
          .build();
    }
  }

  // #supervisor

  public
  // #supervisor2
  static class Supervisor2 extends AbstractActor {

    // #strategy2
    private static SupervisorStrategy strategy =
        new OneForOneStrategy(
            10,
            Duration.ofMinutes(1),
            DeciderBuilder.match(ArithmeticException.class, e -> SupervisorStrategy.resume())
                .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
                .matchAny(o -> SupervisorStrategy.escalate())
                .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
      return strategy;
    }

    // #strategy2

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Props.class,
              props -> {
                getSender().tell(getContext().actorOf(props), getSelf());
              })
          .build();
    }

    @Override
    public void preRestart(Throwable cause, Optional<Object> msg) {
      // do not kill all children, which is the default here
    }
  }

  // #supervisor2

  public
  // #child
  static class Child extends AbstractActor {
    int state = 0;

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Exception.class,
              exception -> {
                throw exception;
              })
          .match(Integer.class, i -> state = i)
          .matchEquals("get", s -> getSender().tell(state, getSelf()))
          .build();
    }
  }

  // #child

  // #testkit
  static ActorSystem system;
  scala.concurrent.duration.Duration timeout =
      scala.concurrent.duration.Duration.create(5, SECONDS);

  @BeforeClass
  public static void start() {
    system = ActorSystem.create("FaultHandlingTest", config);
  }

  @AfterClass
  public static void cleanup() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void mustEmploySupervisorStrategy() throws Exception {
    // code here
    // #testkit
    EventFilter ex1 = new ErrorFilter(ArithmeticException.class);
    EventFilter ex2 = new ErrorFilter(NullPointerException.class);
    EventFilter ex3 = new ErrorFilter(IllegalArgumentException.class);
    EventFilter ex4 = new ErrorFilter(Exception.class);
    EventFilter[] ignoreExceptions = {ex1, ex2, ex3, ex4};
    system.getEventStream().publish(new TestEvent.Mute(immutableSeq(ignoreExceptions)));

    // #create
    Props superprops = Props.create(Supervisor.class);
    ActorRef supervisor = system.actorOf(superprops, "supervisor");
    ActorRef child =
        (ActorRef) Await.result(ask(supervisor, Props.create(Child.class), 5000), timeout);
    // #create

    // #resume
    child.tell(42, ActorRef.noSender());
    assertEquals(42, Await.result(ask(child, "get", 5000), timeout));
    child.tell(new ArithmeticException(), ActorRef.noSender());
    assertEquals(42, Await.result(ask(child, "get", 5000), timeout));
    // #resume

    // #restart
    child.tell(new NullPointerException(), ActorRef.noSender());
    assertEquals(0, Await.result(ask(child, "get", 5000), timeout));
    // #restart

    // #stop
    final TestProbe probe = new TestProbe(system);
    probe.watch(child);
    child.tell(new IllegalArgumentException(), ActorRef.noSender());
    probe.expectMsgClass(Terminated.class);
    // #stop

    // #escalate-kill
    child = (ActorRef) Await.result(ask(supervisor, Props.create(Child.class), 5000), timeout);
    probe.watch(child);
    assertEquals(0, Await.result(ask(child, "get", 5000), timeout));
    child.tell(new Exception(), ActorRef.noSender());
    probe.expectMsgClass(Terminated.class);
    // #escalate-kill

    // #escalate-restart
    superprops = Props.create(Supervisor2.class);
    supervisor = system.actorOf(superprops);
    child = (ActorRef) Await.result(ask(supervisor, Props.create(Child.class), 5000), timeout);
    child.tell(23, ActorRef.noSender());
    assertEquals(23, Await.result(ask(child, "get", 5000), timeout));
    child.tell(new Exception(), ActorRef.noSender());
    assertEquals(0, Await.result(ask(child, "get", 5000), timeout));
    // #escalate-restart
    // #testkit
  }
}
// #testkit

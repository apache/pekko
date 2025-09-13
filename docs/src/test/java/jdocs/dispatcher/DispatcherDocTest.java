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

package jdocs.dispatcher;

import org.apache.pekko.dispatch.ControlMessage;
import org.apache.pekko.dispatch.RequiresMessageQueue;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.ekrich.config.ConfigFactory;
import docs.dispatcher.DispatcherDocSpec;
import jdocs.AbstractJavaTest;
import jdocs.actor.MyBoundedActor;
import jdocs.actor.MyActor;
import org.junit.ClassRule;
import org.junit.Test;
import scala.concurrent.ExecutionContextExecutor;

// #imports
import org.apache.pekko.actor.*;
// #imports
// #imports-prio
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

// #imports-prio

// #imports-prio-mailbox
import org.apache.pekko.dispatch.PriorityGenerator;
import org.apache.pekko.dispatch.UnboundedStablePriorityMailbox;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.ekrich.config.Config;

// #imports-prio-mailbox

// #imports-required-mailbox

// #imports-required-mailbox

public class DispatcherDocTest extends AbstractJavaTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource(
          "DispatcherDocTest",
          ConfigFactory.parseString(DispatcherDocSpec.javaConfig())
              .withFallback(ConfigFactory.parseString(DispatcherDocSpec.config()))
              .withFallback(PekkoSpec.testConf()));

  private final ActorSystem system = actorSystemResource.getSystem();

  @SuppressWarnings("unused")
  @Test
  public void defineDispatcherInConfig() {
    // #defining-dispatcher-in-config
    ActorRef myActor = system.actorOf(Props.create(MyActor.class), "myactor");
    // #defining-dispatcher-in-config
  }

  @SuppressWarnings("unused")
  @Test
  public void defineDispatcherInCode() {
    // #defining-dispatcher-in-code
    ActorRef myActor =
        system.actorOf(Props.create(MyActor.class).withDispatcher("my-dispatcher"), "myactor3");
    // #defining-dispatcher-in-code
  }

  @SuppressWarnings("unused")
  @Test
  public void defineFixedPoolSizeDispatcher() {
    // #defining-fixed-pool-size-dispatcher
    ActorRef myActor =
        system.actorOf(Props.create(MyActor.class).withDispatcher("blocking-io-dispatcher"));
    // #defining-fixed-pool-size-dispatcher
  }

  @SuppressWarnings("unused")
  @Test
  public void definePinnedDispatcher() {
    // #defining-pinned-dispatcher
    ActorRef myActor =
        system.actorOf(Props.create(MyActor.class).withDispatcher("my-pinned-dispatcher"));
    // #defining-pinned-dispatcher
  }

  @SuppressWarnings("unused")
  public void compileLookup() {
    // #lookup
    // this is scala.concurrent.ExecutionContextExecutor, which implements
    // both scala.concurrent.ExecutionContext (for use with Futures, Scheduler, etc.)
    // and java.util.concurrent.Executor (for use with CompletableFuture etc.)
    final ExecutionContextExecutor ex = system.dispatchers().lookup("my-dispatcher");
    // #lookup
  }

  @SuppressWarnings("unused")
  @Test
  public void defineMailboxInConfig() {
    // #defining-mailbox-in-config
    ActorRef myActor = system.actorOf(Props.create(MyActor.class), "priomailboxactor");
    // #defining-mailbox-in-config
  }

  @SuppressWarnings("unused")
  @Test
  public void defineMailboxInCode() {
    // #defining-mailbox-in-code
    ActorRef myActor = system.actorOf(Props.create(MyActor.class).withMailbox("prio-mailbox"));
    // #defining-mailbox-in-code
  }

  @SuppressWarnings("unused")
  @Test
  public void usingARequiredMailbox() {
    ActorRef myActor = system.actorOf(Props.create(MyBoundedActor.class));
  }

  @Test
  public void priorityDispatcher() throws Exception {
    TestKit probe = new TestKit(system);
    // #prio-dispatcher

    class Demo extends AbstractActor {
      LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

      {
        for (Object msg :
            new Object[] {
              "lowpriority",
              "lowpriority",
              "highpriority",
              "pigdog",
              "pigdog2",
              "pigdog3",
              "highpriority",
              PoisonPill.getInstance()
            }) {
          getSelf().tell(msg, getSelf());
        }
      }

      @Override
      public Receive createReceive() {
        return receiveBuilder()
            .matchAny(
                message -> {
                  log.info(message.toString());
                })
            .build();
      }
    }

    // We create a new Actor that just prints out what it processes
    ActorRef myActor =
        system.actorOf(Props.create(Demo.class, this).withDispatcher("prio-dispatcher"));

    /*
    Logs:
      'highpriority
      'highpriority
      'pigdog
      'pigdog2
      'pigdog3
      'lowpriority
      'lowpriority
    */
    // #prio-dispatcher

    probe.watch(myActor);
    probe.expectMsgClass(Terminated.class);
  }

  @Test
  public void controlAwareDispatcher() throws Exception {
    TestKit probe = new TestKit(system);
    // #control-aware-dispatcher

    class Demo extends AbstractActor {
      LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

      {
        for (Object msg :
            new Object[] {"foo", "bar", new MyControlMessage(), PoisonPill.getInstance()}) {
          getSelf().tell(msg, getSelf());
        }
      }

      @Override
      public Receive createReceive() {
        return receiveBuilder()
            .matchAny(
                message -> {
                  log.info(message.toString());
                })
            .build();
      }
    }

    // We create a new Actor that just prints out what it processes
    ActorRef myActor =
        system.actorOf(Props.create(Demo.class, this).withDispatcher("control-aware-dispatcher"));

    /*
    Logs:
      'MyControlMessage
      'foo
      'bar
    */
    // #control-aware-dispatcher

    probe.watch(myActor);
    probe.expectMsgClass(Terminated.class);
  }

  public
  // #prio-mailbox
  static class MyPrioMailbox extends UnboundedStablePriorityMailbox {
    // needed for reflective instantiation
    public MyPrioMailbox(ActorSystem.Settings settings, Config config) {
      // Create a new PriorityGenerator, lower prio means more important
      super(
          new PriorityGenerator() {
            @Override
            public int gen(Object message) {
              if (message.equals("highpriority"))
                return 0; // 'highpriority messages should be treated first if possible
              else if (message.equals("lowpriority"))
                return 2; // 'lowpriority messages should be treated last if possible
              else if (message.equals(PoisonPill.getInstance()))
                return 3; // PoisonPill when no other left
              else return 1; // By default they go between high and low prio
            }
          });
    }
  }
  // #prio-mailbox

  public
  // #control-aware-mailbox-messages
  static class MyControlMessage implements ControlMessage {}
  // #control-aware-mailbox-messages

  @Test
  public void requiredMailboxDispatcher() throws Exception {
    ActorRef myActor =
        system.actorOf(Props.create(MyActor.class).withDispatcher("custom-dispatcher"));
  }

  public
  // #require-mailbox-on-actor
  static class MySpecialActor extends AbstractActor
      implements RequiresMessageQueue<MyUnboundedMessageQueueSemantics> {
    // #require-mailbox-on-actor
    @Override
    public Receive createReceive() {
      return AbstractActor.emptyBehavior();
    }
    // #require-mailbox-on-actor
    // ...
  }
  // #require-mailbox-on-actor

  @Test
  public void requiredMailboxActor() throws Exception {
    ActorRef myActor = system.actorOf(Props.create(MySpecialActor.class));
  }
}

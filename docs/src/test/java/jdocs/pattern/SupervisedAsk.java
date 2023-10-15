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

package jdocs.pattern;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorKilledException;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorRefFactory;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.OneForOneStrategy;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Scheduler;
import org.apache.pekko.actor.Status;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.actor.Terminated;
import org.apache.pekko.pattern.Patterns;

public class SupervisedAsk {

  private static class AskParam {
    Props props;
    Object message;
    Duration timeout;

    AskParam(Props props, Object message, Duration timeout) {
      this.props = props;
      this.message = message;
      this.timeout = timeout;
    }
  }

  private static class AskTimeout {}

  public static class AskSupervisorCreator extends AbstractActor {

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              AskParam.class,
              message -> {
                ActorRef supervisor = getContext().actorOf(Props.create(AskSupervisor.class));
                supervisor.forward(message, getContext());
              })
          .build();
    }
  }

  public static class AskSupervisor extends AbstractActor {
    private ActorRef targetActor;
    private ActorRef caller;
    private AskParam askParam;
    private Cancellable timeoutMessage;

    @Override
    public SupervisorStrategy supervisorStrategy() {
      return new OneForOneStrategy(
          0,
          Duration.ZERO,
          cause -> {
            caller.tell(new Status.Failure(cause), getSelf());
            return SupervisorStrategy.stop();
          });
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              AskParam.class,
              message -> {
                askParam = message;
                caller = getSender();
                targetActor = getContext().actorOf(askParam.props);
                getContext().watch(targetActor);
                targetActor.forward(askParam.message, getContext());
                Scheduler scheduler = getContext().getSystem().scheduler();
                timeoutMessage =
                    scheduler.scheduleOnce(
                        askParam.timeout,
                        getSelf(),
                        new AskTimeout(),
                        getContext().getDispatcher(),
                        null);
              })
          .match(
              Terminated.class,
              message -> {
                Throwable ex = new ActorKilledException("Target actor terminated.");
                caller.tell(new Status.Failure(ex), getSelf());
                timeoutMessage.cancel();
                getContext().stop(getSelf());
              })
          .match(
              AskTimeout.class,
              message -> {
                Throwable ex =
                    new TimeoutException(
                        "Target actor timed out after " + askParam.timeout.toString());
                caller.tell(new Status.Failure(ex), getSelf());
                getContext().stop(getSelf());
              })
          .build();
    }
  }

  public static CompletionStage<Object> askOf(
      ActorRef supervisorCreator, Props props, Object message, Duration timeout) {
    AskParam param = new AskParam(props, message, timeout);
    return Patterns.ask(supervisorCreator, param, timeout);
  }

  public static synchronized ActorRef createSupervisorCreator(ActorRefFactory factory) {
    return factory.actorOf(Props.create(AskSupervisorCreator.class));
  }
}

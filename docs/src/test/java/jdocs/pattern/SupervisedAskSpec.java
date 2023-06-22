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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorRefFactory;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.AbstractActor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class SupervisedAskSpec {

  public Object execute(
      Class<? extends AbstractActor> someActor,
      Object message,
      Duration timeout,
      ActorRefFactory actorSystem)
      throws Exception {
    // example usage
    try {
      ActorRef supervisorCreator = SupervisedAsk.createSupervisorCreator(actorSystem);
      CompletionStage<Object> finished =
          SupervisedAsk.askOf(supervisorCreator, Props.create(someActor), message, timeout);
      return finished.toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      // exception propagated by supervision
      throw e;
    }
  }
}

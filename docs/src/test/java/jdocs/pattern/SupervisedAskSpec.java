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

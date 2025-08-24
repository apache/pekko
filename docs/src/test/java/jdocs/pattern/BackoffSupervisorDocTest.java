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

package jdocs.pattern;

// #backoff-imports
import java.time.Duration;
import org.apache.pekko.actor.*;
import org.apache.pekko.pattern.BackoffOpts;
import org.apache.pekko.pattern.BackoffSupervisor;
import org.apache.pekko.testkit.TestActors.EchoActor;

// #backoff-imports

public class BackoffSupervisorDocTest {

  void exampleStop(ActorSystem system) {
    // #backoff-stop
    final Props childProps = Props.create(EchoActor.class);

    final Props supervisorProps =
        BackoffSupervisor.props(
            BackoffOpts.onStop(
                childProps,
                "myEcho",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                0.2)); // adds 20% "noise" to vary the intervals slightly

    system.actorOf(supervisorProps, "echoSupervisor");
    // #backoff-stop
  }

  void exampleFailure(ActorSystem system) {
    // #backoff-fail
    final Props childProps = Props.create(EchoActor.class);

    final Props supervisorProps =
        BackoffSupervisor.props(
            BackoffOpts.onFailure(
                childProps,
                "myEcho",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                0.2)); // adds 20% "noise" to vary the intervals slightly

    system.actorOf(supervisorProps, "echoSupervisor");
    // #backoff-fail
  }
}

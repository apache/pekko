/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.japi.Creator;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.RestartSettings;
import org.apache.pekko.stream.UniqueKillSwitch;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.RestartSource;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.time.Duration;
import java.util.Arrays;

public class Restart {
  static org.apache.pekko.actor.ActorSystem system = org.apache.pekko.actor.ActorSystem.create();

  public static void onRestartWithBackoffInnerFailure() {
    // #restart-failure-inner-failure
    // could throw if for example it used a database connection to get rows
    Source<Creator<Integer>, NotUsed> flakySource =
        Source.from(
            Arrays.<Creator<Integer>>asList(
                () -> 1,
                () -> 2,
                () -> 3,
                () -> {
                  throw new RuntimeException("darn");
                }));
    Source<Creator<Integer>, NotUsed> forever =
        RestartSource.onFailuresWithBackoff(
            RestartSettings.create(Duration.ofSeconds(1), Duration.ofSeconds(10), 0.1),
            () -> flakySource);
    forever.runWith(
        Sink.foreach((Creator<Integer> nr) -> system.log().info("{}", nr.create())), system);
    // logs
    // [INFO] [12/10/2019 13:51:58.300] [default-pekko.test.stream-dispatcher-7]
    // [pekko.actor.ActorSystemImpl(default)] 1
    // [INFO] [12/10/2019 13:51:58.301] [default-pekko.test.stream-dispatcher-7]
    // [pekko.actor.ActorSystemImpl(default)] 2
    // [INFO] [12/10/2019 13:51:58.302] [default-pekko.test.stream-dispatcher-7]
    // [pekko.actor.ActorSystemImpl(default)] 3
    // [WARN] [12/10/2019 13:51:58.310] [default-pekko.test.stream-dispatcher-7]
    // [RestartWithBackoffSource(pekko://default)] Restarting graph due to failure. stack_trace:
    // (RuntimeException: darn)
    // --> 1 second gap
    // [INFO] [12/10/2019 13:51:59.379] [default-pekko.test.stream-dispatcher-8]
    // [pekko.actor.ActorSystemImpl(default)] 1
    // [INFO] [12/10/2019 13:51:59.382] [default-pekko.test.stream-dispatcher-8]
    // [pekko.actor.ActorSystemImpl(default)] 2
    // [INFO] [12/10/2019 13:51:59.383] [default-pekko.test.stream-dispatcher-8]
    // [pekko.actor.ActorSystemImpl(default)] 3
    // [WARN] [12/10/2019 13:51:59.386] [default-pekko.test.stream-dispatcher-8]
    // [RestartWithBackoffSource(pekko://default)] Restarting graph due to failure. stack_trace:
    // (RuntimeException: darn)
    // --> 2 second gap
    // [INFO] [12/10/2019 13:52:01.594] [default-pekko.test.stream-dispatcher-8]
    // [pekko.actor.ActorSystemImpl(default)] 1
    // [INFO] [12/10/2019 13:52:01.595] [default-pekko.test.stream-dispatcher-8]
    // [pekko.actor.ActorSystemImpl(default)] 2
    // [INFO] [12/10/2019 13:52:01.595] [default-pekko.test.stream-dispatcher-8]
    // [pekko.actor.ActorSystemImpl(default)] 3
    // [WARN] [12/10/2019 13:52:01.596] [default-pekko.test.stream-dispatcher-8]
    // [RestartWithBackoffSource(pekko://default)] Restarting graph due to failure. stack_trace:
    // (RuntimeException: darn)
    // #restart-failure-inner-failure

  }

  public static void onRestartWithBackoffInnerComplete() {
    // #restart-failure-inner-complete
    Source<String, Cancellable> finiteSource =
        Source.tick(Duration.ofSeconds(1), Duration.ofSeconds(1), "tick").take(3);
    Source<String, NotUsed> forever =
        RestartSource.onFailuresWithBackoff(
            RestartSettings.create(Duration.ofSeconds(1), Duration.ofSeconds(10), 0.1),
            () -> finiteSource);
    forever.runWith(Sink.foreach(System.out::println), system);
    // prints
    // tick
    // tick
    // tick
    // #restart-failure-inner-complete
  }

  public static void onRestartWitFailureKillSwitch() {
    // #restart-failure-inner-complete-kill-switch
    Source<Creator<Integer>, NotUsed> flakySource =
        Source.from(
            Arrays.<Creator<Integer>>asList(
                () -> 1,
                () -> 2,
                () -> 3,
                () -> {
                  throw new RuntimeException("darn");
                }));
    UniqueKillSwitch stopRestarting =
        RestartSource.onFailuresWithBackoff(
                RestartSettings.create(Duration.ofSeconds(1), Duration.ofSeconds(10), 0.1),
                () -> flakySource)
            .viaMat(KillSwitches.single(), Keep.right())
            .toMat(Sink.foreach(nr -> System.out.println("nr " + nr.create())), Keep.left())
            .run(system);
    // ... from some where else
    // stop the source from restarting
    stopRestarting.shutdown();
    // #restart-failure-inner-complete-kill-switch
  }
}

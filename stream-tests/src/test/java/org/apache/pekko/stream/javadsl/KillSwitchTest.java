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

package org.apache.pekko.stream.javadsl;

import static org.apache.pekko.Done.done;
import static org.junit.Assert.*;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.Done;
import org.apache.pekko.stream.*;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.Utils;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

public class KillSwitchTest extends StreamTest {
  public KillSwitchTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("KillSwitchTest", PekkoSpec.testConf());

  @Test
  public void beAbleToUseKillSwitch() throws Exception {
    final TestPublisher.Probe<Integer> upstream = TestPublisher.probe(0, system);
    final TestSubscriber.Probe<Integer> downstream = TestSubscriber.probe(system);
    final SharedKillSwitch killSwitch = KillSwitches.shared("testSwitch");

    final SharedKillSwitch k =
        Source.fromPublisher(upstream)
            .viaMat(killSwitch.flow(), Keep.right())
            .to(Sink.fromSubscriber(downstream))
            .run(system);

    final CompletionStage<Done> completionStage =
        Source.single(1).via(killSwitch.flow()).runWith(Sink.ignore(), system);

    downstream.request(1);
    upstream.sendNext(1);
    downstream.expectNext(1);

    assertEquals(killSwitch, k);

    killSwitch.shutdown();

    upstream.expectCancellation();
    downstream.expectComplete();

    assertEquals(completionStage.toCompletableFuture().get(3, TimeUnit.SECONDS), done());
  }

  @Test
  public void beAbleToUseKillSwitchAbort() throws Exception {
    final TestPublisher.Probe<Integer> upstream = TestPublisher.probe(0, system);
    final TestSubscriber.Probe<Integer> downstream = TestSubscriber.probe(system);
    final SharedKillSwitch killSwitch = KillSwitches.shared("testSwitch");

    Source.fromPublisher(upstream)
        .viaMat(killSwitch.flow(), Keep.right())
        .runWith(Sink.fromSubscriber(downstream), system);

    downstream.request(1);
    upstream.sendNext(1);
    downstream.expectNext(1);

    final Exception te = new Utils.TE("Testy");
    killSwitch.abort(te);

    upstream.expectCancellation();
    final Throwable te2 = downstream.expectError();

    assertEquals(te, te2);
  }

  @Test
  public void beAbleToUseSingleKillSwitch() throws Exception {
    final TestPublisher.Probe<Integer> upstream = TestPublisher.probe(0, system);
    final TestSubscriber.Probe<Integer> downstream = TestSubscriber.probe(system);
    final Graph<FlowShape<Integer, Integer>, UniqueKillSwitch> killSwitchFlow =
        KillSwitches.single();

    final UniqueKillSwitch killSwitch =
        Source.fromPublisher(upstream)
            .viaMat(killSwitchFlow, Keep.right())
            .to(Sink.fromSubscriber(downstream))
            .run(system);

    downstream.request(1);
    upstream.sendNext(1);
    downstream.expectNext(1);

    killSwitch.shutdown();

    upstream.expectCancellation();
    downstream.expectComplete();
  }
}

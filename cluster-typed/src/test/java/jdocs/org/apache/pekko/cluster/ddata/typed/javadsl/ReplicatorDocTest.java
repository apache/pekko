/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.cluster.ddata.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.ddata.GCounter;
import org.apache.pekko.cluster.ddata.GCounterKey;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.typed.javadsl.DistributedData;
import org.apache.pekko.cluster.ddata.typed.javadsl.Replicator;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;

import static jdocs.org.apache.pekko.cluster.ddata.typed.javadsl.ReplicatorDocSample.Counter;
import static org.junit.Assert.assertEquals;

public class ReplicatorDocTest extends JUnitSuite {

  static Config config =
      ConfigFactory.parseString(
          "pekko.actor.provider = cluster \n"
              + "pekko.remote.classic.netty.tcp.port = 0 \n"
              + "pekko.remote.artery.canonical.port = 0 \n"
              + "pekko.remote.artery.canonical.hostname = 127.0.0.1 \n");

  @ClassRule public static TestKitJunitResource testKit = new TestKitJunitResource(config);

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void shouldHaveApiForUpdateAndGet() {
    TestProbe<Integer> probe = testKit.createTestProbe(Integer.class);
    ActorRef<Counter.Command> client =
        testKit.spawn(Counter.create(GCounterKey.create("counter1")));

    client.tell(Counter.Increment.INSTANCE);
    client.tell(new Counter.GetValue(probe.getRef()));
    probe.expectMessage(1);
  }

  @Test
  public void shouldHaveApiForSubscribeAndUnsubscribe() {
    TestProbe<Integer> probe = testKit.createTestProbe(Integer.class);
    ActorRef<Counter.Command> client =
        testKit.spawn(Counter.create(GCounterKey.create("counter2")));

    client.tell(Counter.Increment.INSTANCE);
    client.tell(Counter.Increment.INSTANCE);
    probe.awaitAssert(
        () -> {
          client.tell(new Counter.GetCachedValue(probe.getRef()));
          probe.expectMessage(2);
          return null;
        });

    client.tell(Counter.Increment.INSTANCE);
    probe.awaitAssert(
        () -> {
          client.tell(new Counter.GetCachedValue(probe.getRef()));
          probe.expectMessage(3);
          return null;
        });

    client.tell(Counter.Unsubscribe.INSTANCE);
    client.tell(Counter.Increment.INSTANCE);
    // wait so it would update the cached value if we didn't unsubscribe
    probe.expectNoMessage(Duration.ofMillis(500));
    client.tell(new Counter.GetCachedValue(probe.getRef()));
    probe.expectMessage(3); // old value, not 4
  }

  @Test
  public void shouldHaveAnExtension() {
    Key<GCounter> key = GCounterKey.create("counter3");
    ActorRef<Counter.Command> client = testKit.spawn(Counter.create(key));

    TestProbe<Integer> probe = testKit.createTestProbe(Integer.class);
    client.tell(Counter.Increment.INSTANCE);
    client.tell(new Counter.GetValue(probe.getRef()));
    probe.expectMessage(1);

    TestProbe<Replicator.GetResponse<GCounter>> getReplyProbe = testKit.createTestProbe();
    ActorRef<Replicator.Command> replicator = DistributedData.get(testKit.system()).replicator();
    replicator.tell(new Replicator.Get<>(key, Replicator.readLocal(), getReplyProbe.getRef()));
    @SuppressWarnings("unchecked")
    Replicator.GetSuccess<GCounter> rsp =
        getReplyProbe.expectMessageClass(Replicator.GetSuccess.class);
    assertEquals(1, rsp.get(key).getValue().intValue());
  }
}

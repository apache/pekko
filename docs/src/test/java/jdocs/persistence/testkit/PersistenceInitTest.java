/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.persistence.testkit;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;

import com.typesafe.config.ConfigFactory;
import jdocs.AbstractJavaTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

// #imports
import org.apache.pekko.persistence.testkit.javadsl.PersistenceInit;
import org.apache.pekko.Done;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

// #imports

@ExtendWith(TestKitJUnitJupiterExtension.class)
public class PersistenceInitTest extends AbstractJavaTest {
  @JUnitJupiterTestKit
  public ActorTestKit testKit = new JUnitJupiterTestKitBuilder()
      .withCustomConfig(ConfigFactory.parseString(
                  "pekko.persistence.journal.plugin = \"pekko.persistence.journal.inmem\" \n"
                      + "pekko.persistence.journal.inmem.test-serialization = on \n"
                      + "pekko.persistence.snapshot-store.plugin = \"pekko.persistence.snapshot-store.local\" \n"
                      + "pekko.persistence.snapshot-store.local.dir = \"target/snapshot-"
                      + UUID.randomUUID().toString()
                      + "\" \n")
              .withFallback(ConfigFactory.defaultApplication()))
      .build();

  @Test
  public void testInit() throws Exception {
    // #init
    Duration timeout = Duration.ofSeconds(5);
    CompletionStage<Done> done =
        PersistenceInit.initializeDefaultPlugins(testKit.system(), timeout);
    done.toCompletableFuture().get(timeout.getSeconds(), TimeUnit.SECONDS);
    // #init
  }
}

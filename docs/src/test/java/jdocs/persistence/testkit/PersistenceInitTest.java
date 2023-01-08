/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.persistence.testkit;

import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;

import com.typesafe.config.ConfigFactory;
import jdocs.AbstractJavaTest;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.UUID;

// #imports
import org.apache.pekko.persistence.testkit.javadsl.PersistenceInit;
import org.apache.pekko.Done;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

// #imports

public class PersistenceInitTest extends AbstractJavaTest {
  @ClassRule
  public static final TestKitJunitResource testKit =
      new TestKitJunitResource(
          ConfigFactory.parseString(
                  "pekko.persistence.journal.plugin = \"pekko.persistence.journal.inmem\" \n"
                      + "pekko.persistence.journal.inmem.test-serialization = on \n"
                      + "pekko.persistence.snapshot-store.plugin = \"pekko.persistence.snapshot-store.local\" \n"
                      + "pekko.persistence.snapshot-store.local.dir = \"target/snapshot-"
                      + UUID.randomUUID().toString()
                      + "\" \n")
              .withFallback(ConfigFactory.defaultApplication()));

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

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

package jdocs.org.apache.pekko.typed;

import static jdocs.org.apache.pekko.typed.StashDocSample.DB;
import static jdocs.org.apache.pekko.typed.StashDocSample.DataAccess;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.actor.testkit.typed.annotations.JUnitJupiterTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnitJupiterTestKitBuilder;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJUnitJupiterExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitJUnitJupiterExtension.class)
@ExtendWith(LogCapturingExtension.class)
public class StashDocTest {

  @JUnitJupiterTestKit public ActorTestKit testKit = new JUnitJupiterTestKitBuilder().build();

  @Test
  public void stashingExample() throws Exception {
    final DB db =
        new DB() {
          public CompletionStage<Done> save(String id, String value) {
            return CompletableFuture.completedFuture(Done.getInstance());
          }

          public CompletionStage<String> load(String id) {
            return CompletableFuture.completedFuture("TheValue");
          }
        };

    final ActorRef<DataAccess.Command> dataAccess = testKit.spawn(DataAccess.create("17", db));
    TestProbe<String> getInbox = testKit.createTestProbe(String.class);
    dataAccess.tell(new DataAccess.Get(getInbox.getRef()));
    getInbox.expectMessage("TheValue");

    TestProbe<Done> saveInbox = testKit.createTestProbe(Done.class);
    dataAccess.tell(new DataAccess.Save("UpdatedValue", saveInbox.getRef()));
    dataAccess.tell(new DataAccess.Get(getInbox.getRef()));
    saveInbox.expectMessage(Done.getInstance());
    getInbox.expectMessage("UpdatedValue");

    dataAccess.tell(new DataAccess.Get(getInbox.getRef()));
    getInbox.expectMessage("UpdatedValue");
  }
}

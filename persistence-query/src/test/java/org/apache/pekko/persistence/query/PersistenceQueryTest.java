/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.junit.ClassRule;

public class PersistenceQueryTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource(PersistenceQueryTest.class.getName());

  private final ActorSystem system = actorSystemResource.getSystem();

  // compile-only test
  @SuppressWarnings("unused")
  public void shouldExposeJavaDSLFriendlyQueryJournal() throws Exception {
    final DummyJavaReadJournal readJournal =
        PersistenceQuery.get(system).getReadJournalFor(DummyJavaReadJournal.class, "noop-journal");
    final org.apache.pekko.stream.javadsl.Source<String, NotUsed> ids =
        readJournal.persistenceIds();
  }
}

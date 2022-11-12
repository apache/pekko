/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.AkkaJUnitActorSystemResource;
import org.junit.ClassRule;

public class PersistenceQueryTest {

  @ClassRule
  public static AkkaJUnitActorSystemResource actorSystemResource =
      new AkkaJUnitActorSystemResource(PersistenceQueryTest.class.getName());

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

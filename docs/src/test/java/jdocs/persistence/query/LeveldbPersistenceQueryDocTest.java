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

package jdocs.persistence.query;

import java.util.HashSet;
import java.util.Set;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.persistence.journal.WriteEventAdapter;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.PersistenceQuery;
import org.apache.pekko.persistence.query.Sequence;
import org.apache.pekko.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal;
import org.apache.pekko.stream.javadsl.Source;

public class LeveldbPersistenceQueryDocTest {

  final ActorSystem system = ActorSystem.create();

  public void demonstrateReadJournal() {
    // #get-read-journal
    LeveldbReadJournal queries =
        PersistenceQuery.get(system)
            .getReadJournalFor(LeveldbReadJournal.class, LeveldbReadJournal.Identifier());
    // #get-read-journal
  }

  public void demonstrateEventsByPersistenceId() {
    // #EventsByPersistenceId
    LeveldbReadJournal queries =
        PersistenceQuery.get(system)
            .getReadJournalFor(LeveldbReadJournal.class, LeveldbReadJournal.Identifier());

    Source<EventEnvelope, NotUsed> source =
        queries.eventsByPersistenceId("some-persistence-id", 0, Long.MAX_VALUE);
    // #EventsByPersistenceId
  }

  public void demonstrateAllPersistenceIds() {
    // #AllPersistenceIds
    LeveldbReadJournal queries =
        PersistenceQuery.get(system)
            .getReadJournalFor(LeveldbReadJournal.class, LeveldbReadJournal.Identifier());

    Source<String, NotUsed> source = queries.persistenceIds();
    // #AllPersistenceIds
  }

  public void demonstrateEventsByTag() {
    // #EventsByTag
    LeveldbReadJournal queries =
        PersistenceQuery.get(system)
            .getReadJournalFor(LeveldbReadJournal.class, LeveldbReadJournal.Identifier());

    Source<EventEnvelope, NotUsed> source = queries.eventsByTag("green", new Sequence(0L));
    // #EventsByTag
  }

  public
  // #tagger
  static class MyTaggingEventAdapter implements WriteEventAdapter {

    @Override
    public Object toJournal(Object event) {
      if (event instanceof String) {
        String s = (String) event;
        Set<String> tags = new HashSet<String>();
        if (s.contains("green")) tags.add("green");
        if (s.contains("black")) tags.add("black");
        if (s.contains("blue")) tags.add("blue");
        if (tags.isEmpty()) return event;
        else return new Tagged(event, tags);
      } else {
        return event;
      }
    }

    @Override
    public String manifest(Object event) {
      return "";
    }
  }
  // #tagger
}

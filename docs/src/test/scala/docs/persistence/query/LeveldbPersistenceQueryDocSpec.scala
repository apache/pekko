/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.persistence.query

import org.apache.pekko.NotUsed
import org.apache.pekko.testkit.PekkoSpec
import org.apache.pekko.persistence.query.{ EventEnvelope, PersistenceQuery, Sequence }
import org.apache.pekko.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import org.apache.pekko.stream.scaladsl.Source

object LeveldbPersistenceQueryDocSpec {
  // #tagger
  import org.apache.pekko
  import pekko.persistence.journal.WriteEventAdapter
  import pekko.persistence.journal.Tagged

  class MyTaggingEventAdapter extends WriteEventAdapter {
    val colors = Set("green", "black", "blue")
    override def toJournal(event: Any): Any = event match {
      case s: String =>
        val tags = colors.foldLeft(Set.empty[String]) { (acc, c) =>
          if (s.contains(c)) acc + c else acc
        }
        if (tags.isEmpty) event
        else Tagged(event, tags)
      case _ => event
    }

    override def manifest(event: Any): String = ""
  }
  // #tagger
}

class LeveldbPersistenceQueryDocSpec
    extends PekkoSpec("pekko.persistence.journal.plugin = pekko.persistence.journal.leveldb") {

  "LeveldbPersistentQuery" must {
    "demonstrate how get ReadJournal" in {
      // #get-read-journal
      import org.apache.pekko
      import pekko.persistence.query.PersistenceQuery
      import pekko.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal

      val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
      // #get-read-journal
    }

    "demonstrate EventsByPersistenceId" in {
      // #EventsByPersistenceId
      val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

      val src: Source[EventEnvelope, NotUsed] =
        queries.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)

      val events: Source[Any, NotUsed] = src.map(_.event)
      // #EventsByPersistenceId
    }

    "demonstrate AllPersistenceIds" in {
      // #AllPersistenceIds
      val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

      val src: Source[String, NotUsed] = queries.persistenceIds()
      // #AllPersistenceIds
    }

    "demonstrate EventsByTag" in {
      // #EventsByTag
      val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

      val src: Source[EventEnvelope, NotUsed] =
        queries.eventsByTag(tag = "green", offset = Sequence(0L))
      // #EventsByTag
    }

  }

}

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

package org.apache.pekko.persistence.query

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ExtendedActorSystem
import pekko.stream.scaladsl.Source
import pekko.util.unused

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Use for tests only!
 * Emits infinite stream of strings (representing queried for events).
 */
class DummyReadJournal(val dummyValue: String) extends scaladsl.ReadJournal with scaladsl.PersistenceIdsQuery {
  override def persistenceIds(): Source[String, NotUsed] =
    Source.fromIterator(() => Iterator.from(0)).map(_.toString)
}

object DummyReadJournal {
  final val Identifier = "pekko.persistence.query.journal.dummy"
}

class DummyReadJournalForJava(readJournal: DummyReadJournal)
    extends javadsl.ReadJournal
    with javadsl.PersistenceIdsQuery {
  override def persistenceIds(): pekko.stream.javadsl.Source[String, NotUsed] =
    readJournal.persistenceIds().asJava
}

object DummyReadJournalProvider {
  final val config: Config = ConfigFactory.parseString(s"""
      ${DummyReadJournal.Identifier} {
        class = "${classOf[DummyReadJournalProvider].getCanonicalName}"
      }
      ${DummyReadJournal.Identifier}2 {
        class = "${classOf[DummyReadJournalProvider2].getCanonicalName}"
      }
      ${DummyReadJournal.Identifier}3 {
        class = "${classOf[DummyReadJournalProvider3].getCanonicalName}"
      }
      ${DummyReadJournal.Identifier}4 {
        class = "${classOf[DummyReadJournalProvider4].getCanonicalName}"
      }
      ${DummyReadJournal.Identifier}5 {
        class = "${classOf[DummyReadJournalProvider5].getCanonicalName}"
      }
    """)
}

class DummyReadJournalProvider(dummyValue: String) extends ReadJournalProvider {

  // mandatory zero-arg constructor
  def this() = this("dummy")

  val readJournal = new DummyReadJournal(dummyValue)

  override def scaladslReadJournal(): DummyReadJournal =
    readJournal

  val javaReadJournal = new DummyReadJournalForJava(readJournal)

  override def javadslReadJournal(): DummyReadJournalForJava =
    javaReadJournal
}

class DummyReadJournalProvider2(@unused sys: ExtendedActorSystem) extends DummyReadJournalProvider

class DummyReadJournalProvider3(@unused sys: ExtendedActorSystem, @unused conf: Config) extends DummyReadJournalProvider

class DummyReadJournalProvider4(@unused sys: ExtendedActorSystem, @unused conf: Config, @unused confPath: String)
    extends DummyReadJournalProvider

class DummyReadJournalProvider5(@unused sys: ExtendedActorSystem) extends DummyReadJournalProvider

class CustomDummyReadJournalProvider5(@unused sys: ExtendedActorSystem) extends DummyReadJournalProvider("custom")

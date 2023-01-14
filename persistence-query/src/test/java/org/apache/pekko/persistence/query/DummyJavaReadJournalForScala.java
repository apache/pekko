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

/** Use for tests only! Emits infinite stream of strings (representing queried for events). */
public class DummyJavaReadJournalForScala
    implements org.apache.pekko.persistence.query.scaladsl.ReadJournal,
        org.apache.pekko.persistence.query.scaladsl.PersistenceIdsQuery {

  public static final String Identifier = DummyJavaReadJournal.Identifier;

  private final DummyJavaReadJournal readJournal;

  public DummyJavaReadJournalForScala(DummyJavaReadJournal readJournal) {
    this.readJournal = readJournal;
  }

  @Override
  public org.apache.pekko.stream.scaladsl.Source<String, NotUsed> persistenceIds() {
    return readJournal.persistenceIds().asScala();
  }
}

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

import java.util.Iterator;

import org.apache.pekko.NotUsed;
import org.apache.pekko.persistence.query.javadsl.PersistenceIdsQuery;
import org.apache.pekko.persistence.query.javadsl.ReadJournal;
import org.apache.pekko.stream.javadsl.Source;

/** Use for tests only! Emits infinite stream of strings (representing queried for events). */
public class DummyJavaReadJournal implements ReadJournal, PersistenceIdsQuery {
  public static final String Identifier = "pekko.persistence.query.journal.dummy-java";

  @Override
  public Source<String, NotUsed> persistenceIds() {
    return Source.fromIterator(
        () ->
            new Iterator<String>() {
              private int i = 0;

              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public String next() {
                return "" + (i++);
              }
            });
  }
}

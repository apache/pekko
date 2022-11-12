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
  public static final String Identifier = "akka.persistence.query.journal.dummy-java";

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

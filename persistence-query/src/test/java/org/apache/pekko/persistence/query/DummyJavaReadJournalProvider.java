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

package org.apache.pekko.persistence.query;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class DummyJavaReadJournalProvider implements ReadJournalProvider {

  public static final Config config =
      ConfigFactory.parseString(
          DummyJavaReadJournal.Identifier
              + " { \n"
              + "   class = \""
              + DummyJavaReadJournalProvider.class.getCanonicalName()
              + "\" \n"
              + " }\n\n");

  private final DummyJavaReadJournal readJournal = new DummyJavaReadJournal();

  @Override
  public DummyJavaReadJournalForScala scaladslReadJournal() {
    return new DummyJavaReadJournalForScala(readJournal);
  }

  @Override
  public DummyJavaReadJournal javadslReadJournal() {
    return readJournal;
  }
}

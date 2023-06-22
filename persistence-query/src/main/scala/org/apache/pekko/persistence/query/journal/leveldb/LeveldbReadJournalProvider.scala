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

package org.apache.pekko.persistence.query.journal.leveldb

import com.typesafe.config.Config
import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.query.ReadJournalProvider

@deprecated("Use another journal/query implementation", "Akka 2.6.15")
class LeveldbReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  val readJournal: scaladsl.LeveldbReadJournal = new scaladsl.LeveldbReadJournal(system, config)

  override def scaladslReadJournal(): pekko.persistence.query.scaladsl.ReadJournal =
    readJournal

  val javaReadJournal = new javadsl.LeveldbReadJournal(readJournal)

  override def javadslReadJournal(): pekko.persistence.query.javadsl.ReadJournal =
    javaReadJournal

}

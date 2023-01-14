/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query
import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class PersistenceTestKitReadJournalProvider(system: ExtendedActorSystem, config: Config, configPath: String)
    extends ReadJournalProvider {
  private val _scaladslReadJournal =
    new scaladsl.PersistenceTestKitReadJournal(system, config, configPath)
  override def scaladslReadJournal(): scaladsl.PersistenceTestKitReadJournal =
    _scaladslReadJournal

  override def javadslReadJournal(): javadsl.PersistenceTestKitReadJournal =
    new javadsl.PersistenceTestKitReadJournal(_scaladslReadJournal)
}

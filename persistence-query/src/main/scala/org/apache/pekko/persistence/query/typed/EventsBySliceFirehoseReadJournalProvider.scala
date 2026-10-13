/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed
import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.query.ReadJournalProvider

final class EventsBySliceFirehoseReadJournalProvider(system: ExtendedActorSystem, config: Config, cfgPath: String)
    extends ReadJournalProvider {

  private lazy val scaladslReadJournalInstance: scaladsl.EventsBySliceFirehoseQuery =
    new scaladsl.EventsBySliceFirehoseQuery(system, config, cfgPath)

  override def scaladslReadJournal(): scaladsl.EventsBySliceFirehoseQuery = scaladslReadJournalInstance

  private lazy val javadslReadJournalInstance =
    new javadsl.EventsBySliceFirehoseQuery(new scaladsl.EventsBySliceFirehoseQuery(system, config, cfgPath))

  override def javadslReadJournal(): javadsl.EventsBySliceFirehoseQuery = javadslReadJournalInstance
}

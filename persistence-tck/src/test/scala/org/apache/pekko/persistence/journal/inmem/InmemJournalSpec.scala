/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.journal.inmem

import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.PersistenceSpec
import pekko.persistence.journal.JournalSpec

class InmemJournalSpec extends JournalSpec(config = PersistenceSpec.config("inmem", "InmemJournalSpec")) {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.off()
}

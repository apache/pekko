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

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.persistence.testkit.state.scaladsl

import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.state.DurableStateStoreSpec
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

import com.typesafe.config.ConfigFactory

object PersistenceTestKitDurableStateStoreTCKSpec {
  val config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    """))
}

class PersistenceTestKitDurableStateStoreTCKSpec
    extends DurableStateStoreSpec(PersistenceTestKitDurableStateStoreTCKSpec.config) {
  override protected def supportsDeleteWithRevisionCheck: CapabilityFlag = CapabilityFlag.off()
}

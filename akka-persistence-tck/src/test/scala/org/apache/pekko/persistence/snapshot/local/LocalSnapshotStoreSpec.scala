/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.snapshot.local

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.PluginCleanup
import pekko.persistence.snapshot.SnapshotStoreSpec

class LocalSnapshotStoreSpec
    extends SnapshotStoreSpec(
      config =
        ConfigFactory.parseString("""
    akka.test.timefactor = 3
    akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
    akka.persistence.snapshot-store.local.dir = "target/snapshots"
    """))
    with PluginCleanup {

  override protected def supportsSerialization: CapabilityFlag = CapabilityFlag.on()
}

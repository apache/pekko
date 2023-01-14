/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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
    pekko.test.timefactor = 3
    pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
    pekko.persistence.snapshot-store.local.dir = "target/snapshots"
    """))
    with PluginCleanup {

  override protected def supportsSerialization: CapabilityFlag = CapabilityFlag.on()
}

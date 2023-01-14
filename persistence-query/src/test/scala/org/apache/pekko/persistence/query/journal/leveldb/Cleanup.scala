/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.journal.leveldb

import java.io.File

import org.apache.commons.io.FileUtils

import org.apache.pekko.testkit.PekkoSpec

trait Cleanup { this: PekkoSpec =>
  val storageLocations =
    List(
      "pekko.persistence.journal.leveldb.dir",
      "pekko.persistence.journal.leveldb-shared.store.dir",
      "pekko.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override protected def atStartup(): Unit = {
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  override protected def afterTermination(): Unit = {
    storageLocations.foreach(FileUtils.deleteDirectory)
  }
}

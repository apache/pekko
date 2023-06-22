/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

trait PluginCleanup extends BeforeAndAfterAll { self: PluginSpec =>
  val storageLocations =
    List("pekko.persistence.journal.leveldb.dir", "pekko.persistence.snapshot-store.local.dir").map(s =>
      new File(system.settings.config.getString(s)))

  override def beforeAll(): Unit = {
    storageLocations.foreach(FileUtils.deleteDirectory)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    storageLocations.foreach(FileUtils.deleteDirectory)
  }
}

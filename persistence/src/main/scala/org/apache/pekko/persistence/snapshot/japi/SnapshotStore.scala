/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.snapshot.japi

import scala.concurrent.Future

import org.apache.pekko
import pekko.util.FutureConverters._
import pekko.japi.Util._
import pekko.persistence._
import pekko.persistence.snapshot.{ SnapshotStore => SSnapshotStore }

/**
 * Java API: abstract snapshot store.
 */
abstract class SnapshotStore extends SSnapshotStore with SnapshotStorePlugin {
  import context.dispatcher

  override final def loadAsync(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] =
    doLoadAsync(persistenceId, criteria).asScala.map(option)

  override final def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    doSaveAsync(metadata, snapshot).asScala.map(_ => ())

  override final def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    doDeleteAsync(metadata).asScala.map(_ => ())

  override final def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    doDeleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria).asScala.map(_ => ())

}

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

package org.apache.pekko.persistence.typed.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.persistence.{ SnapshotMetadata => ClassicSnapshotMetadata }
import pekko.persistence.{ SnapshotSelectionCriteria => ClassicSnapshotSelectionCriteria }
import pekko.persistence.SelectedSnapshot
import pekko.persistence.snapshot.SnapshotStore
import pekko.persistence.typed.scaladsl.SnapshotMutableStateSpec.MutableState

class SlowInMemorySnapshotStore extends SnapshotStore {

  private var state = Map.empty[String, (Any, ClassicSnapshotMetadata)]

  def loadAsync(persistenceId: String, criteria: ClassicSnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    Future.successful(state.get(persistenceId).map {
      case (snap, meta) => SelectedSnapshot(meta, snap)
    })
  }

  def saveAsync(metadata: ClassicSnapshotMetadata, snapshot: Any): Future[Unit] = {
    val snapshotState = snapshot.asInstanceOf[MutableState]
    val value1 = snapshotState.value
    Thread.sleep(50)
    val value2 = snapshotState.value
    // it mustn't have been modified by another command/event
    if (value1 != value2)
      Future.failed(new IllegalStateException(s"State changed from $value1 to $value2"))
    else {
      // copy to simulate serialization, and subsequent recovery shouldn't get same instance
      state = state.updated(metadata.persistenceId, (new MutableState(snapshotState.value), metadata))
      Future.successful(())
    }
  }

  override def deleteAsync(metadata: ClassicSnapshotMetadata): Future[Unit] = {
    state = state.filterNot {
      case (pid, (_, meta)) => pid == metadata.persistenceId && meta.sequenceNr == metadata.sequenceNr
    }
    Future.successful(())
  }

  override def deleteAsync(persistenceId: String, criteria: ClassicSnapshotSelectionCriteria): Future[Unit] = {
    state = state.filterNot {
      case (pid, (_, meta)) => pid == persistenceId && criteria.matches(meta)
    }
    Future.successful(())
  }
}

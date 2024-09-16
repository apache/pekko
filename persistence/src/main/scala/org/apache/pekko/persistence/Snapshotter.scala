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

package org.apache.pekko.persistence

import org.apache.pekko
import pekko.actor._
import pekko.persistence.SnapshotProtocol._

/**
 * Snapshot API on top of the internal snapshot protocol.
 */
trait Snapshotter extends Actor {

  /** Snapshot store plugin actor. */
  private[persistence] def snapshotStore: ActorRef

  /**
   * Snapshotter id.
   */
  def snapshotterId: String

  /**
   * Sequence number to use when taking a snapshot.
   */
  def snapshotSequenceNr: Long

  /**
   * Instructs the snapshot store to load the specified snapshot and send it via an [[pekko.persistence.SnapshotOffer SnapshotOffer]]
   * to the running [[pekko.persistence.PersistentActor PersistentActor]].
   */
  def loadSnapshot(persistenceId: String, criteria: SnapshotSelectionCriteria, toSequenceNr: Long): Unit =
    snapshotStore ! LoadSnapshot(persistenceId, criteria, toSequenceNr)

  /**
   * Saves a `snapshot` of this snapshotter's state.
   *
   * The [[pekko.persistence.PersistentActor PersistentActor]] will be notified about the success or failure of this
   * via an [[pekko.persistence.SaveSnapshotSuccess SaveSnapshotSuccess]] or [[pekko.persistence.SaveSnapshotFailure SaveSnapshotFailure]] message.
   */
  def saveSnapshot(snapshot: Any): Unit =
    snapshotStore ! SaveSnapshot(SnapshotMetadata(snapshotterId, snapshotSequenceNr), snapshot)

  /**
   * Deletes the snapshot identified by `sequenceNr`.
   *
   * The [[pekko.persistence.PersistentActor PersistentActor]] will be notified about the status of the deletion
   * via an [[pekko.persistence.DeleteSnapshotSuccess DeleteSnapshotSuccess]] or [[pekko.persistence.DeleteSnapshotFailure DeleteSnapshotFailure]] message.
   */
  def deleteSnapshot(sequenceNr: Long): Unit =
    snapshotStore ! DeleteSnapshot(SnapshotMetadata(snapshotterId, sequenceNr))

  /**
   * Deletes all snapshots matching `criteria`.
   *
   * The [[pekko.persistence.PersistentActor PersistentActor]] will be notified about the status of the deletion
   * via an [[pekko.persistence.DeleteSnapshotsSuccess DeleteSnapshotsSuccess]] or [[pekko.persistence.DeleteSnapshotsFailure DeleteSnapshotsFailure]] message.
   */
  def deleteSnapshots(criteria: SnapshotSelectionCriteria): Unit =
    snapshotStore ! DeleteSnapshots(snapshotterId, criteria)

}

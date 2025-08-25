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

package org.apache.pekko.persistence.snapshot.japi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;

interface SnapshotStorePlugin {
  // #snapshot-store-plugin-api
  /**
   * Java API, Plugin API: asynchronously loads a snapshot.
   *
   * @param persistenceId id of the persistent actor.
   * @param criteria selection criteria for loading.
   * @return a CompletionStage that will be completed when the loading is done (in Pekko 1.x, this was a Scala Future)
   */
  CompletionStage<Optional<SelectedSnapshot>> doLoadAsync(
      String persistenceId, SnapshotSelectionCriteria criteria);

  /**
   * Java API, Plugin API: asynchronously saves a snapshot.
   *
   * @param metadata snapshot metadata.
   * @param snapshot snapshot.
   * @return a CompletionStage that will be completed when the saving is done (in Pekko 1.x, this was a Scala Future)
   */
  CompletionStage<Void> doSaveAsync(SnapshotMetadata metadata, Object snapshot);

  /**
   * Java API, Plugin API: deletes the snapshot identified by `metadata`.
   *
   * @param metadata snapshot metadata.
   * @return a CompletionStage that will be completed when the deletion is done (in Pekko 1.x, this was a Scala Future)
   */
  CompletionStage<Void> doDeleteAsync(SnapshotMetadata metadata);

  /**
   * Java API, Plugin API: deletes all snapshots matching `criteria`.
   *
   * @param persistenceId id of the persistent actor.
   * @param criteria selection criteria for deleting.
   * @return a CompletionStage that will be completed when the deletion is done (in Pekko 1.x, this was a Scala Future)
   */
  CompletionStage<Void> doDeleteAsync(String persistenceId, SnapshotSelectionCriteria criteria);
  // #snapshot-store-plugin-api
}

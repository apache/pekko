/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.javadsl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.typed.SnapshotSelectionCriteria
import pekko.persistence.typed.internal.{ DefaultRecovery, DisabledRecovery, RecoveryWithSnapshotSelectionCriteria }

/**
 * Strategy for recovery of snapshots and events.
 */
abstract class Recovery {
  def asScala: pekko.persistence.typed.scaladsl.Recovery

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def toClassic: pekko.persistence.Recovery
}

/**
 * Strategy for recovery of snapshots and events.
 */
object Recovery {

  /**
   * Snapshots and events are recovered
   */
  val default: Recovery = DefaultRecovery

  /**
   * Neither snapshots nor events are recovered
   */
  val disabled: Recovery = DisabledRecovery

  /**
   * Changes the snapshot selection criteria used for the recovery.
   *
   * By default the most recent snapshot is used, and the remaining state updates are recovered by replaying events
   * from the sequence number up until which the snapshot reached.
   *
   * You may configure the behavior to skip replaying snapshots completely, in which case the recovery will be
   * performed by replaying all events -- which may take a long time.
   */
  def withSnapshotSelectionCriteria(snapshotSelectionCriteria: SnapshotSelectionCriteria) =
    RecoveryWithSnapshotSelectionCriteria(snapshotSelectionCriteria)

}

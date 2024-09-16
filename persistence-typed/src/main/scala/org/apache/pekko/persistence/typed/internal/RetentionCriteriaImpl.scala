/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.typed.javadsl
import pekko.persistence.typed.scaladsl

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class SnapshotCountRetentionCriteriaImpl(
    snapshotEveryNEvents: Int,
    keepNSnapshots: Int,
    deleteEventsOnSnapshot: Boolean)
    extends javadsl.SnapshotCountRetentionCriteria
    with scaladsl.SnapshotCountRetentionCriteria {

  require(snapshotEveryNEvents > 0, s"'snapshotEveryNEvents' must be greater than 0, was [$snapshotEveryNEvents]")
  require(keepNSnapshots > 0, s"'keepNSnapshots' must be greater than 0, was [$keepNSnapshots]")

  def snapshotWhen(currentSequenceNr: Long): Boolean =
    currentSequenceNr % snapshotEveryNEvents == 0

  def deleteUpperSequenceNr(lastSequenceNr: Long): Long =
    // Delete old events, retain the latest
    math.max(0, lastSequenceNr - (keepNSnapshots.toLong * snapshotEveryNEvents))

  def deleteLowerSequenceNr(upperSequenceNr: Long): Long =
    // We could use 0 as fromSequenceNr to delete all older snapshots, but that might be inefficient for
    // large ranges depending on how it's implemented in the snapshot plugin. Therefore we use the
    // same window as defined for how much to keep in the retention criteria
    math.max(0, upperSequenceNr - (keepNSnapshots.toLong * snapshotEveryNEvents))

  override def withDeleteEventsOnSnapshot: SnapshotCountRetentionCriteriaImpl =
    copy(deleteEventsOnSnapshot = true)

  override def asScala: scaladsl.RetentionCriteria = this

  override def asJava: javadsl.RetentionCriteria = this
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case object DisabledRetentionCriteria
    extends javadsl.RetentionCriteria
    with scaladsl.RetentionCriteria {
  override def asScala: scaladsl.RetentionCriteria = this
  override def asJava: javadsl.RetentionCriteria = this
}

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.typed.{ javadsl, scaladsl, SnapshotSelectionCriteria }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case object DefaultRecovery extends javadsl.Recovery with scaladsl.Recovery {
  override def asScala: scaladsl.Recovery = this
  override def asJava: javadsl.Recovery = this

  /**
   * INTERNAL API
   */
  override private[pekko] def toClassic = pekko.persistence.Recovery()
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case object DisabledRecovery extends javadsl.Recovery with scaladsl.Recovery {
  override def asScala: scaladsl.Recovery = this
  override def asJava: javadsl.Recovery = this

  /**
   * INTERNAL API
   */
  override private[pekko] def toClassic = pekko.persistence.Recovery.none
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] case class RecoveryWithSnapshotSelectionCriteria(
    snapshotSelectionCriteria: SnapshotSelectionCriteria)
    extends javadsl.Recovery
    with scaladsl.Recovery {
  override def asScala: scaladsl.Recovery = this
  override def asJava: javadsl.Recovery = this

  /**
   * INTERNAL API
   */
  override private[pekko] def toClassic = pekko.persistence.Recovery(snapshotSelectionCriteria.toClassic)
}

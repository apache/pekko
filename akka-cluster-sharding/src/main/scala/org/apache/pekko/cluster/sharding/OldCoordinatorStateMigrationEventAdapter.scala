/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ShardCoordinator.Internal.ShardHomeAllocated
import pekko.persistence.journal.EventAdapter
import pekko.persistence.journal.EventSeq

/**
 * Used for migrating from persistent state store mode to the new event sourced remember entities. No user API,
 * used through configuration. See reference docs for details.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class OldCoordinatorStateMigrationEventAdapter extends EventAdapter {
  override def manifest(event: Any): String =
    ""

  override def toJournal(event: Any): Any =
    event

  override def fromJournal(event: Any, manifest: String): EventSeq = {
    event match {
      case ShardHomeAllocated(shardId, _) =>
        EventSeq.single(shardId)
      case _ => EventSeq.empty
    }

  }
}

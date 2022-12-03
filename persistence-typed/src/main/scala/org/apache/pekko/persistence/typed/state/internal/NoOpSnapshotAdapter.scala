/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.typed.SnapshotAdapter

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] class NoOpSnapshotAdapter extends SnapshotAdapter[Any] {
  override def toJournal(state: Any): Any = state
  override def fromJournal(from: Any): Any = from
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object NoOpSnapshotAdapter {
  val i = new NoOpSnapshotAdapter
  def instance[S]: SnapshotAdapter[S] = i.asInstanceOf[SnapshotAdapter[S]]
}

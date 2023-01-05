/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.javadsl

import java.time.Duration
import java.util.Optional

import scala.compat.java8.OptionConverters._

import org.apache.pekko
import pekko.japi.function.Function3
import pekko.persistence.typed.SnapshotAdapter
import pekko.util.JavaDurationConverters._

/**
 * Helper functions for migration from PersistentFSM to Persistence Typed
 */
object PersistentFSMMigration {

  /**
   * Create a snapshot adapter that will adapt snapshots created by a PersistentFSM into
   * the correct State type of a [[EventSourcedBehavior]]
   * @param adapt Takes in the state identifier, snapshot persisted by the PersistentFSM and the state timeout and
   *              returns the `State` that should be given to the the [[EventSourcedBehavior]]
   * @tparam State State type of the [[EventSourcedBehavior]]
   * @return A [[SnapshotAdapter]] to be used with a [[EventSourcedBehavior]]
   */
  def snapshotAdapter[State](adapt: Function3[String, Any, Optional[Duration], State]): SnapshotAdapter[State] =
    pekko.persistence.typed.scaladsl.PersistentFSMMigration.snapshotAdapter((stateId, snapshot, timer) =>
      adapt.apply(stateId, snapshot, timer.map(_.asJava).asJava))
}

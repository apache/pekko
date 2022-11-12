/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.state.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done

/**
 * API for updating durable state objects.
 *
 * For Java API see [[pekko.persistence.state.javadsl.DurableStateUpdateStore]].
 */
trait DurableStateUpdateStore[A] extends DurableStateStore[A] {

  /**
   * @param seqNr sequence number for optimistic locking. starts at 1.
   */
  def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): Future[Done]

  @deprecated(message = "Use the deleteObject overload with revision instead.", since = "2.6.20")
  def deleteObject(persistenceId: String): Future[Done]

  def deleteObject(persistenceId: String, revision: Long): Future[Done]
}

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

package org.apache.pekko.persistence.state.javadsl

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.Done

/**
 * API for updating durable state objects.
 *
 * For Scala API see [[pekko.persistence.state.scaladsl.DurableStateUpdateStore]].
 */
trait DurableStateUpdateStore[A] extends DurableStateStore[A] {

  /**
   * @param seqNr sequence number for optimistic locking. starts at 1.
   */
  def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): CompletionStage[Done]

  @deprecated(message = "Use the deleteObject overload with revision instead.", since = "Akka 2.6.20")
  def deleteObject(persistenceId: String): CompletionStage[Done]

  def deleteObject(persistenceId: String, revision: Long): CompletionStage[Done]
}

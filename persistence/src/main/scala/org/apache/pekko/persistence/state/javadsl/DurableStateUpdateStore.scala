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
   * Upsert the object with the given `persistenceId` and `revision`.
   *
   * @param persistenceId the persistenceId of the object to upsert
   * @param revision the revision of the object to upsert
   * @param value the value to upsert
   * @param tag a tag to associate with the object
   * @return a CompletionStage that completes when the object has been upserted
   */
  def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): CompletionStage[Done]

  /**
   * Delete the object with the given `persistenceId` and `revision`.
   *
   * <p>
   * If the revision does not match the expected revision
   * of the object, the delete operation will fail. The returned CompletionStage
   * will complete with a failed result wrapping the exception.
   * </p>
   *
   * @param persistenceId the persistenceId of the object to delete
   * @param revision the expected next revision for the `persistenceId` — this must be one more than
   *                 the current (existing) revision of the object
   * @return a CompletionStage that completes when the object has been deleted or fails if the revision does not match the expected revision of the object
   */
  def deleteObject(persistenceId: String, revision: Long): CompletionStage[Done]
}

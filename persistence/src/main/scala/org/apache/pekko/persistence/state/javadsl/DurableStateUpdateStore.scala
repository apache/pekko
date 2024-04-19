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
   * Delete the object with the given `persistenceId`. This deprecated
   * function ignores whether the object is deleted or not.
   *
   * @param persistenceId the persistenceId of the object to delete
   * @param revision the revision of the object to delete
   * @return a CompletionStage that completes when the object has been deleted
   */
  @deprecated(message = "Use the deleteObject overload with revision instead.", since = "Akka 2.6.20")
  def deleteObject(persistenceId: String): CompletionStage[Done]

  /**
   * Delete the object with the given `persistenceId` and `revision`.
   *
   * <p>
   * Since Pekko v1.1, if the revision does not match the current revision
   * of the object, the delete operation will fail. The returned CompletionStage
   * will complete with a failed result wrapping the exception.
   * </p>
   *
   * @param persistenceId the persistenceId of the object to delete
   * @param revision the revision of the object to delete
   * @return a CompletionStage that completes when the object has been deleted
   */
  def deleteObject(persistenceId: String, revision: Long): CompletionStage[Done]
}

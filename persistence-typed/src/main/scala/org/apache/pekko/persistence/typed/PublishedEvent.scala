/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import java.util.Optional

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.persistence.typed.internal.ReplicatedPublishedEventMetaData

/**
 * When using event publishing the events published to the system event stream will be in this form.
 *
 * Not for user extension
 */
@DoNotInherit
trait PublishedEvent {

  /** Scala API: When emitted from an Replicated Event Sourcing actor this will contain the replica id */
  def replicatedMetaData: Option[ReplicatedPublishedEventMetaData]

  /** Java API: When emitted from an Replicated Event Sourcing actor this will contain the replica id */
  def getReplicatedMetaData: Optional[ReplicatedPublishedEventMetaData]

  def persistenceId: PersistenceId
  def sequenceNumber: Long

  /** User event */
  def event: Any
  def timestamp: Long
  def tags: Set[String]

  /**
   * If the published event is tagged, return a new published event with the payload unwrapped and the tags dropped,
   * if it is not tagged return the published event as is.
   */
  def withoutTags: PublishedEvent
}

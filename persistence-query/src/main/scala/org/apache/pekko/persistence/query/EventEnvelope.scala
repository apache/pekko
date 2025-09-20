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

package org.apache.pekko.persistence.query

import java.util.Optional

import scala.runtime.AbstractFunction5

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.HashCode

// for binary compatibility (used to be a case class)
object EventEnvelope extends AbstractFunction5[Offset, String, Long, Any, Long, EventEnvelope] {
  def apply(offset: Offset, persistenceId: String, sequenceNr: Long, event: Any, timestamp: Long): EventEnvelope =
    new EventEnvelope(offset, persistenceId, sequenceNr, event, timestamp, None)

  def apply(
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Any,
      timestamp: Long,
      meta: Option[Any]): EventEnvelope =
    new EventEnvelope(offset, persistenceId, sequenceNr, event, timestamp, meta)

  def unapply(arg: EventEnvelope): Option[(Offset, String, Long, Any)] =
    Some((arg.offset, arg.persistenceId, arg.sequenceNr, arg.event))

}

/**
 * Event wrapper adding meta data for the events in the result stream of
 * [[pekko.persistence.query.scaladsl.EventsByTagQuery]] query, or similar queries.
 *
 * The `timestamp` is the time the event was stored, in milliseconds since midnight, January 1, 1970 UTC
 * (same as `System.currentTimeMillis`).
 */
final class EventEnvelope(
    val offset: Offset,
    val persistenceId: String,
    val sequenceNr: Long,
    val event: Any,
    val timestamp: Long,
    val eventMetadata: Option[Any])
    extends Product4[Offset, String, Long, Any]
    with Serializable {

  // bin compat 2.6.7
  def this(offset: Offset, persistenceId: String, sequenceNr: Long, event: Any, timestamp: Long) =
    this(offset, persistenceId, sequenceNr, event, timestamp, None)

  /**
   * Java API
   */
  def getEventMetaData(): Optional[Any] = {
    import scala.jdk.OptionConverters._
    eventMetadata.toJava
  }

  override def hashCode(): Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, offset)
    result = HashCode.hash(result, persistenceId)
    result = HashCode.hash(result, sequenceNr)
    result = HashCode.hash(result, event)
    result
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: EventEnvelope =>
      offset == other.offset && persistenceId == other.persistenceId && sequenceNr == other.sequenceNr &&
      event == other.event // timestamp && metadata not included in equals for backwards compatibility
    case _ => false
  }

  override def toString: String =
    s"EventEnvelope($offset,$persistenceId,$sequenceNr,$event,$timestamp,$eventMetadata)"

  // for binary compatibility (used to be a case class)
  def copy(
      offset: Offset = this.offset,
      persistenceId: String = this.persistenceId,
      sequenceNr: Long = this.sequenceNr,
      event: Any = this.event): EventEnvelope =
    new EventEnvelope(offset, persistenceId, sequenceNr, event, timestamp, this.eventMetadata)

  @InternalApi
  private[pekko] def withMetadata(metadata: Any): EventEnvelope =
    new EventEnvelope(offset, persistenceId, sequenceNr, event, timestamp, Some(metadata))

  // Product4, for binary compatibility (used to be a case class)
  override def productPrefix = "EventEnvelope"
  override def _1: Offset = offset
  override def _2: String = persistenceId
  override def _3: Long = sequenceNr
  override def _4: Any = event
  override def canEqual(that: Any): Boolean = that.isInstanceOf[EventEnvelope]

}

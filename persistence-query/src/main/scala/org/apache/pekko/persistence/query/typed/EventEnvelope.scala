/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.query.typed

import java.util.Optional
import java.util.{ Set => JSet }

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.persistence.query.Offset
import pekko.util.HashCode

object EventEnvelope {

  def apply[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long,
      entityType: String,
      slice: Int,
      filtered: Boolean,
      source: String,
      tags: Set[String]): EventEnvelope[Event] =
    new EventEnvelope(
      offset,
      persistenceId,
      sequenceNr,
      Option(event),
      timestamp,
      None,
      entityType,
      slice,
      filtered,
      source,
      tags)

  def apply[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long,
      entityType: String,
      slice: Int): EventEnvelope[Event] =
    new EventEnvelope(offset, persistenceId, sequenceNr, Option(event), timestamp, None, entityType, slice)

  def apply[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long,
      entityType: String,
      slice: Int,
      filtered: Boolean,
      source: String): EventEnvelope[Event] =
    new EventEnvelope(
      offset,
      persistenceId,
      sequenceNr,
      Option(event),
      timestamp,
      None,
      entityType,
      slice,
      filtered,
      source)

  def create[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long,
      entityType: String,
      slice: Int,
      filtered: Boolean,
      source: String,
      tags: JSet[String]): EventEnvelope[Event] = {
    import scala.jdk.CollectionConverters._
    apply(offset, persistenceId, sequenceNr, event, timestamp, entityType, slice, filtered, source, tags.asScala.toSet)
  }

  def create[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long,
      entityType: String,
      slice: Int): EventEnvelope[Event] =
    apply(offset, persistenceId, sequenceNr, event, timestamp, entityType, slice)

  def create[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long,
      entityType: String,
      slice: Int,
      filtered: Boolean,
      source: String): EventEnvelope[Event] =
    apply(offset, persistenceId, sequenceNr, event, timestamp, entityType, slice, filtered, source)

  def unapply[Event](arg: EventEnvelope[Event]): Option[(Offset, String, Long, Option[Event], Long)] =
    Some((arg.offset, arg.persistenceId, arg.sequenceNr, arg.eventOption, arg.timestamp))
}

/**
 * Event wrapper adding meta data for the events in the result stream of
 * [[pekko.persistence.query.typed.scaladsl.EventsBySliceQuery]] query, or similar queries.
 *
 * If the `event` is not defined it has not been loaded yet. It can be loaded with
 * [[pekko.persistence.query.typed.scaladsl.LoadEventQuery]].
 *
 * The `timestamp` is the time the event was stored, in milliseconds since midnight, January 1, 1970 UTC (same as
 * `System.currentTimeMillis`).
 *
 * It is an improved `EventEnvelope` compared to [[pekko.persistence.query.EventEnvelope]].
 *
 * API May Change
 */
@ApiMayChange
final class EventEnvelope[Event](
    val offset: Offset,
    val persistenceId: String,
    val sequenceNr: Long,
    val eventOption: Option[Event],
    val timestamp: Long,
    val eventMetadata: Option[Any],
    val entityType: String,
    val slice: Int,
    val filtered: Boolean,
    val source: String,
    val tags: Set[String]) {

  def this(
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      eventOption: Option[Event],
      timestamp: Long,
      eventMetadata: Option[Any],
      entityType: String,
      slice: Int,
      filtered: Boolean,
      source: String) =
    this(
      offset,
      persistenceId,
      sequenceNr,
      eventOption,
      timestamp,
      eventMetadata,
      entityType,
      slice,
      filtered,
      source,
      tags = Set.empty)

  def this(
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      eventOption: Option[Event],
      timestamp: Long,
      eventMetadata: Option[Any],
      entityType: String,
      slice: Int) =
    this(offset, persistenceId, sequenceNr, eventOption, timestamp, eventMetadata, entityType, slice, false, "")

  def event: Event =
    eventOption match {
      case Some(evt) => evt
      case None      =>
        throw new IllegalStateException(
          "Event was not loaded. Use eventOption and load the event on demand with LoadEventQuery.")
    }

  /**
   * Java API
   */
  def getEvent(): Event =
    eventOption match {
      case Some(evt) => evt
      case None      =>
        throw new IllegalStateException(
          "Event was not loaded. Use getOptionalEvent and load the event on demand with LoadEventQuery.")
    }

  /**
   * Java API
   */
  def getOptionalEvent(): Optional[Event] = {
    import scala.jdk.OptionConverters._
    eventOption.toJava
  }

  /**
   * Java API
   */
  def getEventMetaData(): Optional[AnyRef] = {
    import scala.jdk.OptionConverters._
    eventMetadata.toJava.asInstanceOf[Optional[AnyRef]]
  }

  /**
   * Java API:
   */
  def getTags(): JSet[String] = {
    import scala.jdk.CollectionConverters._
    tags.asJava
  }

  override def hashCode(): Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, offset)
    result = HashCode.hash(result, persistenceId)
    result = HashCode.hash(result, sequenceNr)
    result
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: EventEnvelope[_] =>
      offset == other.offset && persistenceId == other.persistenceId && sequenceNr == other.sequenceNr &&
      eventOption == other.eventOption && timestamp == other.timestamp && eventMetadata == other.eventMetadata &&
      entityType == other.entityType && slice == other.slice && filtered == other.filtered &&
      tags == other.tags
    case _ => false
  }

  override def toString: String = {
    val eventStr = eventOption match {
      case Some(evt) => evt.getClass.getName
      case None      => ""
    }
    val metaStr = eventMetadata match {
      case Some(meta) => meta.getClass.getName
      case None       => ""
    }
    s"EventEnvelope($offset,$persistenceId,$sequenceNr,$eventStr,$timestamp,$metaStr,$entityType,$slice,$filtered,$source,${tags
        .mkString("[", ", ", "]")})"
  }
}

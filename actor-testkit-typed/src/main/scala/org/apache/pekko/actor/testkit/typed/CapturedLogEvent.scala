/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed

import java.util.Optional

import org.slf4j.Marker
import org.slf4j.event.Level

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.OptionConverters._
import pekko.util.OptionVal

/**
 * Representation of a Log Event issued by a [[pekko.actor.typed.Behavior]]
 * when testing with [[pekko.actor.testkit.typed.scaladsl.BehaviorTestKit]]
 * or [[pekko.actor.testkit.typed.javadsl.BehaviorTestKit]].
 */
final case class CapturedLogEvent(level: Level, message: String, cause: Option[Throwable], marker: Option[Marker]) {

  /**
   * Constructor for Java API
   */
  def this(
      level: Level,
      message: String,
      errorCause: Optional[Throwable],
      marker: Optional[Marker],
      mdc: java.util.Map[String, Any]) =
    this(level, message, errorCause.toScala, marker.toScala)

  /**
   * Constructor for Java API
   */
  def this(level: Level, message: String) =
    this(level, message, Option.empty, Option.empty)

  /**
   * Constructor for Java API
   */
  def this(level: Level, message: String, errorCause: Throwable) =
    this(level, message, Some(errorCause), Option.empty[Marker])

  /**
   * Constructor for Java API
   */
  def this(level: Level, message: String, marker: Marker) =
    this(level, message, Option.empty[Throwable], Some(marker))

  /**
   * Constructor for Java API
   */
  def this(level: Level, message: String, errorCause: Throwable, marker: Marker) =
    this(level, message, Some(errorCause), Some(marker))

  def getErrorCause: Optional[Throwable] = cause.toJava

  def getMarker: Optional[Marker] = marker.toJava
}

object CapturedLogEvent {

  /**
   * Helper method to convert [[OptionVal]] to [[Option]]
   */
  private def toOption[A](optionVal: OptionVal[A]): Option[A] = optionVal match {
    case OptionVal.Some(x) => Some(x)
    case _                 => None
  }

  def apply(level: Level, message: String): CapturedLogEvent = {
    CapturedLogEvent(level, message, None, None)
  }

  /**
   * Auxiliary constructor that receives Pekko's internal [[OptionVal]] as parameters and converts them to Scala's [[Option]].
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def apply(
      level: Level,
      message: String,
      errorCause: OptionVal[Throwable],
      logMarker: OptionVal[Marker]): CapturedLogEvent = {
    new CapturedLogEvent(level, message, toOption(errorCause), toOption(logMarker))
  }
}

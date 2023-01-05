/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed

import java.util.Optional

import scala.compat.java8.OptionConverters._

import org.slf4j.Marker
import org.slf4j.event.Level

import org.apache.pekko
import pekko.util.ccompat.JavaConverters._

object LoggingEvent {

  /**
   * Scala API
   */
  def apply(level: Level, loggerName: String, threadName: String, message: String, timeStamp: Long): LoggingEvent =
    new LoggingEvent(level, loggerName, threadName, message, timeStamp, None, None, Map.empty)

  /**
   * Java API
   */
  def create(level: Level, loggerName: String, threadName: String, message: String, timeStamp: Long): LoggingEvent =
    apply(level, loggerName, threadName, message, timeStamp)

  /**
   * Java API
   */
  def create(
      level: Level,
      loggerName: String,
      threadName: String,
      message: String,
      timeStamp: Long,
      marker: Optional[Marker],
      throwable: Optional[Throwable],
      mdc: java.util.Map[String, String]) =
    apply(level, loggerName, threadName, message, timeStamp, marker.asScala, throwable.asScala, mdc.asScala.toMap)
}

/**
 * Representation of logging event when testing with [[pekko.actor.testkit.typed.scaladsl.LoggingTestKit]]
 * or [[pekko.actor.testkit.typed.javadsl.LoggingTestKit]].
 */
final case class LoggingEvent(
    level: Level,
    loggerName: String,
    threadName: String,
    message: String,
    timeStamp: Long,
    marker: Option[Marker],
    throwable: Option[Throwable],
    mdc: Map[String, String]) {

  /**
   * Java API
   */
  def getMarker: Optional[Marker] =
    marker.asJava

  /**
   * Java API
   */
  def getThrowable: Optional[Throwable] =
    throwable.asJava

  /**
   * Java API
   */
  def getMdc: java.util.Map[String, String] = {
    import pekko.util.ccompat.JavaConverters._
    mdc.asJava
  }

}

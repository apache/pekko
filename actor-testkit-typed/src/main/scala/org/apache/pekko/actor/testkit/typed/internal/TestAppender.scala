/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.internal

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import org.slf4j.{ MDC, Marker }
import org.apache.pekko
import pekko.actor.testkit.typed.LoggingEvent
import pekko.annotation.InternalApi

import java.util.Collections

/**
 * INTERNAL API
 *
 * The `TestAppender` emits the logging events to the registered [[LoggingTestKitImpl]], which
 * are added and removed to the appender dynamically from tests.
 *
 * `TestAppender` is currently requiring Logback as SLF4J implementation.
 * Similar can probably be implemented with other backends, such as Log4j2.
 */
@InternalApi private[pekko] object TestAppender {
  import LogbackUtil._

  private val TestAppenderName = "PekkoTestAppender"

  def setupTestAppender(loggerName: String): Unit = {
    val logbackLogger = getLogbackLogger(loggerName)
    logbackLogger.getAppender(TestAppenderName) match {
      case null =>
        val testAppender = new TestAppender
        testAppender.setName(TestAppenderName)
        testAppender.setContext(logbackLogger.getLoggerContext)
        testAppender.start()
        logbackLogger.addAppender(testAppender)
      case _: TestAppender =>
      // ok, already setup
      case other =>
        throw new IllegalStateException(s"Unexpected $TestAppenderName already added: $other")
    }
  }

  def addFilter(loggerName: String, filter: LoggingTestKitImpl): Unit =
    getTestAppender(loggerName).addTestFilter(filter)

  def removeFilter(loggerName: String, filter: LoggingTestKitImpl): Unit =
    getTestAppender(loggerName).removeTestFilter(filter)

  private def getTestAppender(loggerName: String): TestAppender = {
    val logger = getLogbackLogger(loggerName)
    logger.getAppender(TestAppenderName) match {
      case testAppender: TestAppender => testAppender
      case null =>
        throw new IllegalStateException(s"No $TestAppenderName was setup for logger [${logger.getName}]")
      case other =>
        throw new IllegalStateException(
          s"Unexpected $TestAppenderName already added for logger [${logger.getName}]: $other")
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class TestAppender extends AppenderBase[ILoggingEvent] {
  import LogbackUtil._

  private var filters: List[LoggingTestKitImpl] = Nil

  // invocations are synchronized via doAppend in AppenderBase
  override def append(event: ILoggingEvent): Unit = {
    import pekko.util.ccompat.JavaConverters._

    val throwable = event.getThrowableProxy match {
      case p: ThrowableProxy =>
        Option(p.getThrowable)
      case _ => None
    }

    val marker: Option[Marker] = Option(event.getMarkerList).flatMap(_.asScala.headOption)
    val mdc: Map[String, String] = Option(event.getMDCPropertyMap)
      .filterNot(_.isEmpty)
      .orElse(Option(MDC.getMDCAdapter.getCopyOfContextMap))
      .getOrElse(Collections.emptyMap())
      .asScala.toMap

    val loggingEvent = LoggingEvent(
      level = convertLevel(event.getLevel),
      message = event.getFormattedMessage,
      loggerName = event.getLoggerName,
      threadName = event.getThreadName,
      timeStamp = event.getTimeStamp,
      marker = marker,
      throwable = throwable,
      mdc = mdc)

    filter(loggingEvent)
  }

  private def filter(event: LoggingEvent): Boolean = {
    filters.exists(f =>
      try {
        f.apply(event)
      } catch {
        case _: Exception => false
      })
  }

  def addTestFilter(filter: LoggingTestKitImpl): Unit = synchronized {
    filters ::= filter
  }

  def removeTestFilter(filter: LoggingTestKitImpl): Unit = synchronized {
    @scala.annotation.tailrec
    def removeFirst(list: List[LoggingTestKitImpl], zipped: List[LoggingTestKitImpl] = Nil): List[LoggingTestKitImpl] =
      list match {
        case head :: tail if head == filter => tail.reverse_:::(zipped)
        case head :: tail                   => removeFirst(tail, head :: zipped)
        case Nil                            => filters // filter not found, just return original list
      }
    filters = removeFirst(filters)
  }

}

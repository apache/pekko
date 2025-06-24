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

package org.apache.pekko.event.jul

import java.util.logging

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorSystem
import pekko.dispatch.RequiresMessageQueue
import pekko.event.DummyClassForStringSources
import pekko.event.EventStream
import pekko.event.LoggerMessageQueueSemantics
import pekko.event.Logging._
import pekko.event.LoggingFilter
import pekko.util.unused

/**
 * `java.util.logging` logger.
 */
@deprecated("Use Slf4jLogger instead.", "Akka 2.6.0")
class JavaLogger extends Actor with RequiresMessageQueue[LoggerMessageQueueSemantics] {
  import Logger.mapLevel

  def receive = {
    case event @ Error(cause, _, _, _) => log(mapLevel(event.level), cause, event)
    case event: Warning                => log(mapLevel(event.level), null, event)
    case event: Info                   => log(mapLevel(event.level), null, event)
    case event: Debug                  => log(mapLevel(event.level), null, event)
    case InitializeLogger(_)           =>
      Logger(this.getClass.getName)
        .warning(s"${getClass.getName} has been deprecated since Akka 2.6.0. Use SLF4J instead.")
      sender() ! LoggerInitialized
  }

  def log(level: logging.Level, cause: Throwable, event: LogEvent): Unit = {
    val logger = Logger(event.logClass, event.logSource)
    val record = new logging.LogRecord(level, String.valueOf(event.message))
    record.setLoggerName(logger.getName)
    record.setThrown(cause)
    record.setThreadID(event.thread.getId.toInt)
    record.setSourceClassName(event.logClass.getName)
    record.setSourceMethodName(null) // lost forever
    logger.log(record)
  }
}

/**
 * Base trait for all classes that wants to be able use the JUL logging infrastructure.
 */
@deprecated("Use SLF4J or direct java.util.logging instead.", "Akka 2.6.0")
trait JavaLogging {
  @transient
  lazy val log: logging.Logger = Logger(this.getClass.getName)
}

/**
 * Logger is a factory for obtaining JUL Loggers
 */
@deprecated("Use SLF4J or direct java.util.logging instead.", "Akka 2.6.0")
object Logger {

  /**
   * @param logger - which logger
   * @return a Logger that corresponds for the given logger name
   */
  def apply(logger: String): logging.Logger = logging.Logger.getLogger(logger)

  /**
   * @param logClass - the class to log for
   * @param logSource - the textual representation of the source of this log stream
   * @return a Logger for the specified parameters
   */
  def apply(logClass: Class[_], logSource: String): logging.Logger = logClass match {
    case c if c == classOf[DummyClassForStringSources] => apply(logSource)
    case _                                             => logging.Logger.getLogger(logClass.getName)
  }

  /**
   * Returns the JUL Root Logger
   */
  def root: logging.Logger = logging.Logger.getGlobal()

  def mapLevel(level: LogLevel): logging.Level = level.asInt match {
    case InfoLevel.asInt    => logging.Level.INFO
    case DebugLevel.asInt   => logging.Level.CONFIG
    case WarningLevel.asInt => logging.Level.WARNING
    case ErrorLevel.asInt   => logging.Level.SEVERE
    case _                  => logging.Level.FINE
  }
}

/**
 * [[pekko.event.LoggingFilter]] that uses the log level defined in the JUL
 * backend configuration to filter log events before publishing
 * the log events to the `eventStream`.
 */
@deprecated("Use Slf4jLoggingFilter instead.", "Akka 2.6.0")
class JavaLoggingFilter(@unused settings: ActorSystem.Settings, eventStream: EventStream) extends LoggingFilter {
  import Logger.mapLevel

  def isErrorEnabled(logClass: Class[_], logSource: String) =
    (eventStream.logLevel >= ErrorLevel) && Logger(logClass, logSource).isLoggable(mapLevel(ErrorLevel))
  def isWarningEnabled(logClass: Class[_], logSource: String) =
    (eventStream.logLevel >= WarningLevel) && Logger(logClass, logSource).isLoggable(mapLevel(WarningLevel))
  def isInfoEnabled(logClass: Class[_], logSource: String) =
    (eventStream.logLevel >= InfoLevel) && Logger(logClass, logSource).isLoggable(mapLevel(InfoLevel))
  def isDebugEnabled(logClass: Class[_], logSource: String) =
    (eventStream.logLevel >= DebugLevel) && Logger(logClass, logSource).isLoggable(mapLevel(DebugLevel))
}

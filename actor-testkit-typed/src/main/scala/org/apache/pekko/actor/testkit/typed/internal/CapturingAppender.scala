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
import ch.qos.logback.core.AppenderBase

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object CapturingAppender {
  import LogbackUtil._

  private val CapturingAppenderName = "CapturingAppender"

  def get(loggerName: String): CapturingAppender = {
    val logbackLogger = getLogbackLogger(loggerName)
    logbackLogger.getAppender(CapturingAppenderName) match {
      case null =>
        throw new IllegalStateException(
          s"$CapturingAppenderName not defined for [${loggerNameOrRoot(loggerName)}] in logback-test.xml")
      case appender: CapturingAppender => appender
      case other =>
        throw new IllegalStateException(s"Unexpected $CapturingAppender: $other")
    }
  }

}

/**
 * INTERNAL API
 *
 * Logging from tests can be silenced by this appender. When there is a test failure
 * the captured logging events are flushed to the appenders defined for the
 * org.apache.pekko.actor.testkit.typed.internal.CapturingAppenderDelegate logger.
 *
 * The flushing on test failure is handled by [[pekko.actor.testkit.typed.scaladsl.LogCapturing]]
 * for ScalaTest and [[pekko.actor.testkit.typed.javadsl.LogCapturing]] for JUnit.
 *
 * Use configuration like the following the logback-test.xml:
 *
 * {{{
 *     <appender name="CapturingAppender" class="org.apache.pekko.actor.testkit.typed.internal.CapturingAppender" />
 *
 *     <logger name="org.apache.pekko.actor.testkit.typed.internal.CapturingAppenderDelegate" >
 *       <appender-ref ref="STDOUT"/>
 *     </logger>
 *
 *     <root level="DEBUG">
 *         <appender-ref ref="CapturingAppender"/>
 *     </root>
 * }}}
 */
@InternalApi private[pekko] class CapturingAppender extends AppenderBase[ILoggingEvent] {
  import LogbackUtil._

  private var buffer: Vector[ILoggingEvent] = Vector.empty

  // invocations are synchronized via doAppend in AppenderBase
  override def append(event: ILoggingEvent): Unit = {
    event.prepareForDeferredProcessing()
    buffer :+= event
  }

  /**
   * Flush buffered logging events to the output appenders
   * Also clears the buffer..
   */
  def flush(): Unit = synchronized {
    import pekko.util.ccompat.JavaConverters._
    val logbackLogger = getLogbackLogger(classOf[CapturingAppender].getName + "Delegate")
    val appenders = logbackLogger.iteratorForAppenders().asScala.filterNot(_ == this).toList
    for (event <- buffer; appender <- appenders)
      appender.doAppend(event)
    clear()
  }

  /**
   * Discards the buffered logging events without output.
   */
  def clear(): Unit = synchronized {
    buffer = Vector.empty
  }

}

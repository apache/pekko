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

package org.apache.pekko.actor.testkit.typed.javadsl

import scala.util.control.NonFatal

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import org.apache.pekko.actor.testkit.typed.internal.CapturingAppender

import org.slf4j.LoggerFactory

/**
 * JUnit `TestRule` to make log lines appear only when the test failed.
 *
 * Use this in test by adding a public field annotated with `@TestRule`:
 * {{{
 *   @Rule public final LogCapturing logCapturing = new LogCapturing();
 * }}}
 *
 * Requires Logback and configuration like the following the logback-test.xml:
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
final class LogCapturing extends TestRule {
  // eager access of CapturingAppender to fail fast if misconfigured
  private val capturingAppender = CapturingAppender.get("")

  private val myLogger = LoggerFactory.getLogger(classOf[LogCapturing])

  override def apply(base: Statement, description: Description): Statement = {
    new Statement {
      override def evaluate(): Unit = {
        try {
          myLogger.info(s"Logging started for test [${description.getClassName}: ${description.getMethodName}]")
          base.evaluate()
          myLogger.info(
            s"Logging finished for test [${description.getClassName}: ${description.getMethodName}] that was successful")
        } catch {
          case NonFatal(e) =>
            println(
              s"--> [${Console.BLUE}${description.getClassName}: ${description.getMethodName}${Console.RESET}] " +
              s"Start of log messages of test that failed with ${e.getMessage}")
            capturingAppender.flush()
            println(
              s"<-- [${Console.BLUE}${description.getClassName}: ${description.getMethodName}${Console.RESET}] " +
              s"End of log messages of test that failed with ${e.getMessage}")
            throw e
        } finally {
          capturingAppender.clear()
        }
      }
    }
  }
}

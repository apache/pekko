/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.actor.testkit.typed.javadsl

import java.lang.reflect.Method

import scala.util.control.NonFatal

import org.junit.jupiter.api.extension.{ ExtensionContext, InvocationInterceptor, ReflectiveInvocationContext }
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation

import org.apache.pekko.actor.testkit.typed.internal.CapturingAppender

import org.slf4j.LoggerFactory

final class LogCapturingExtension extends InvocationInterceptor {

  private val capturingAppender = CapturingAppender.get("")

  private val myLogger = LoggerFactory.getLogger(classOf[LogCapturing])

  @throws[Throwable]
  override def interceptTestMethod(invocation: Invocation[Void], invocationContext: ReflectiveInvocationContext[Method],
      extensionContext: ExtensionContext): Unit = {

    val testClassName = invocationContext.getTargetClass.getSimpleName
    val testMethodName = invocationContext.getExecutable.getName

    try {
      myLogger.info(s"Logging started for test [${testClassName}: ${testMethodName}]")
      invocation.proceed
      myLogger.info(
        s"Logging finished for test [${testClassName}: ${testMethodName}] that was successful")
    } catch {
      case NonFatal(e) =>
        println(
          s"--> [${Console.BLUE}${testClassName}: ${testMethodName}${Console.RESET}] " +
          s"Start of log messages of test that failed with ${e.getMessage}")
        capturingAppender.flush()
        println(
          s"<-- [${Console.BLUE}${testClassName}: ${testMethodName}${Console.RESET}] " +
          s"End of log messages of test that failed with ${e.getMessage}")
        throw e
    } finally {

      capturingAppender.clear()
    }
  }
}

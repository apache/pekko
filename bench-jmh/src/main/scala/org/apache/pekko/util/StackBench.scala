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

package org.apache.pekko.util

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{ Benchmark, Measurement, Scope, State }

@State(Scope.Benchmark)
@Measurement(timeUnit = TimeUnit.MICROSECONDS)
class StackBench {

  class CustomSecurtyManager extends SecurityManager {
    def getTrace: Array[Class[?]] =
      getClassContext
  }

  @Benchmark
  def currentThread(): Array[StackTraceElement] = {
    Thread.currentThread().getStackTrace
  }

  @Benchmark
  def securityManager(): Array[Class[?]] = {
    (new CustomSecurtyManager).getTrace
  }

}

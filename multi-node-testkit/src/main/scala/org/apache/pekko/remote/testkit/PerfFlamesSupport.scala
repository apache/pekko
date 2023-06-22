/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.testkit

import java.io.File

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.remote.testconductor.RoleName

/**
 * INTERNAL API: Support trait allowing trivially recording perf metrics from [[MultiNodeSpec]]s
 */
private[pekko] trait PerfFlamesSupport { self: MultiNodeSpec =>

  /**
   * Runs `perf-java-flames` script on given node (JVM process).
   * Refer to https://github.com/jrudolph/perf-map-agent for options and manual.
   *
   * Options are currently to be passed in via `export PERF_MAP_OPTIONS` etc.
   */
  def runPerfFlames(nodes: RoleName*)(delay: FiniteDuration): Unit = {
    if (isPerfJavaFlamesAvailable && isNode(nodes: _*)) {
      import scala.concurrent.ExecutionContext.Implicits.global

      val afterDelay = pekko.pattern.after(delay, system.scheduler)(Future.successful("GO!"))
      afterDelay.onComplete { _ =>
        import java.lang.management._
        val name = ManagementFactory.getRuntimeMXBean.getName
        val pid = name.substring(0, name.indexOf('@')).toInt

        val perfCommand = s"$perfJavaFlamesPath $pid"
        println(s"[perf @ $myself($pid)][OUT]: " + perfCommand)

        import scala.sys.process._
        perfCommand.run(new ProcessLogger {
          override def buffer[T](f: => T): T = f
          override def out(s: => String): Unit = println(s"[perf @ $myself($pid)][OUT] " + s)
          override def err(s: => String): Unit = println(s"[perf @ $myself($pid)][ERR] " + s)
        })
      }
    }
  }

  def perfJavaFlamesPath: String =
    "/home/ubuntu/perf-java-flames"

  def isPerfJavaFlamesAvailable: Boolean = {
    val isIt = new File(perfJavaFlamesPath).exists()
    if (!isIt) println(s"WARN: perf-java-flames not available under [$perfJavaFlamesPath]! Skipping perf profiling.")
    isIt
  }

}

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

package org.apache.pekko.remote.artery

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import scala.util.Try

import org.apache.pekko.actor.ActorSystem

/**
 * Simple to file logger for benchmark results. Will log relevant settings first to make sure
 * results can be understood later.
 */
trait BenchmarkFileReporter {
  def testName: String
  def reportResults(result: String): Unit
  def close(): Unit
}
object BenchmarkFileReporter {
  val targetDirectory = {
    val target = new File("stream-tests/target/benchmark-results")
    target.mkdirs()
    target
  }

  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

  def apply(test: String, system: ActorSystem): BenchmarkFileReporter = {
    val settingsToReport =
      Seq(
        "org.apache.pekko.test.MaxThroughputSpec.totalMessagesFactor",
        "org.apache.pekko.test.MaxThroughputSpec.real-message",
        "org.apache.pekko.test.LatencySpec.totalMessagesFactor",
        "org.apache.pekko.test.LatencySpec.repeatCount",
        "org.apache.pekko.test.LatencySpec.real-message",
        "pekko.remote.artery.enabled",
        "pekko.remote.artery.advanced.inbound-lanes",
        "pekko.remote.artery.advanced.buffer-pool-size",
        "pekko.remote.artery.advanced.aeron.idle-cpu-level",
        "pekko.remote.artery.advanced.aeron.embedded-media-driver",
        "pekko.remote.default-remote-dispatcher.throughput",
        "pekko.remote.default-remote-dispatcher.fork-join-executor.parallelism-factor",
        "pekko.remote.default-remote-dispatcher.fork-join-executor.parallelism-min",
        "pekko.remote.default-remote-dispatcher.fork-join-executor.parallelism-max")
    apply(test, system, settingsToReport)
  }

  def apply(test: String, system: ActorSystem, settingsToReport: Seq[String]): BenchmarkFileReporter =
    new BenchmarkFileReporter {
      override val testName = test

      val gitCommit = {
        import sys.process._
        Try("git describe".!!.trim).getOrElse("[unknown]")
      }
      val testResultFile: File = {
        val timestamp = formatter.format(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()))
        val fileName = s"$timestamp-Artery-$testName-$gitCommit-results.txt"
        new File(targetDirectory, fileName)
      }
      val config = system.settings.config

      val fos = Files.newOutputStream(testResultFile.toPath)
      reportResults(s"Git commit: $gitCommit")

      settingsToReport.foreach(reportSetting)

      def reportResults(result: String): Unit = synchronized {
        println(result)
        fos.write(result.getBytes(StandardCharsets.UTF_8))
        fos.write('\n')
        fos.flush()
      }

      def reportSetting(name: String): Unit = {
        val value = if (config.hasPath(name)) config.getString(name) else "[unset]"
        reportResults(s"$name: $value")
      }

      def close(): Unit = fos.close()
    }
}

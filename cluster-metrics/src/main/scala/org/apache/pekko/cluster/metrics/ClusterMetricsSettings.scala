/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.metrics

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import org.apache.pekko
import pekko.util.Helpers.ConfigOps
import pekko.util.Helpers.Requiring

/**
 * Metrics extension settings. Documented in: `src/main/resources/reference.conf`.
 */
case class ClusterMetricsSettings(config: Config) {

  private val cc = config.getConfig("pekko.cluster.metrics")

  // Extension.
  val MetricsDispatcher: String = cc.getString("dispatcher")
  val PeriodicTasksInitialDelay: FiniteDuration = cc.getMillisDuration("periodic-tasks-initial-delay")
  val NativeLibraryExtractFolder: String = cc.getString("native-library-extract-folder")

  // Supervisor.
  val SupervisorName: String = cc.getString("supervisor.name")
  val SupervisorStrategyProvider: String = cc.getString("supervisor.strategy.provider")
  val SupervisorStrategyConfiguration: Config = cc.getConfig("supervisor.strategy.configuration")

  // Collector.
  val CollectorEnabled: Boolean = cc.getBoolean("collector.enabled")
  val CollectorProvider: String = cc.getString("collector.provider")
  val CollectorFallback: Boolean = cc.getBoolean("collector.fallback")
  val CollectorSampleInterval: FiniteDuration = {
    cc.getMillisDuration("collector.sample-interval")
  }.requiring(_ > Duration.Zero, "collector.sample-interval must be > 0")
  val CollectorGossipInterval: FiniteDuration = {
    cc.getMillisDuration("collector.gossip-interval")
  }.requiring(_ > Duration.Zero, "collector.gossip-interval must be > 0")
  val CollectorMovingAverageHalfLife: FiniteDuration = {
    cc.getMillisDuration("collector.moving-average-half-life")
  }.requiring(_ > Duration.Zero, "collector.moving-average-half-life must be > 0")

}

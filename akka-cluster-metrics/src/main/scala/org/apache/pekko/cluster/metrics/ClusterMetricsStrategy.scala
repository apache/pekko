/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.metrics

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.OneForOneStrategy
import pekko.util.Helpers.ConfigOps

/**
 * Default [[ClusterMetricsSupervisor]] strategy:
 * A configurable [[pekko.actor.OneForOneStrategy]] with restart-on-throwable decider.
 */
class ClusterMetricsStrategy(config: Config)
    extends OneForOneStrategy(
      maxNrOfRetries = config.getInt("maxNrOfRetries"),
      withinTimeRange = config.getMillisDuration("withinTimeRange"),
      loggingEnabled = config.getBoolean("loggingEnabled"))(ClusterMetricsStrategy.metricsDecider)

/**
 * Provide custom metrics strategy resources.
 */
object ClusterMetricsStrategy {
  import pekko.actor._
  import pekko.actor.SupervisorStrategy._

  /**
   * [[pekko.actor.SupervisorStrategy]] `Decider` which allows to survive intermittent Sigar native method calls failures.
   */
  val metricsDecider: SupervisorStrategy.Decider = {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException         => Stop
    case _: DeathPactException           => Stop
    case _: Throwable                    => Restart
  }

}

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

package org.apache.pekko.cluster.metrics

import org.apache.pekko
import pekko.actor.OneForOneStrategy
import pekko.util.Helpers.ConfigOps

import com.typesafe.config.Config

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

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

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import language.postfixOps

import org.apache.pekko
import pekko.dispatch.Dispatchers
import pekko.testkit.PekkoSpec

class ClusterMetricsSettingsSpec extends PekkoSpec {

  "ClusterMetricsSettings" must {

    "be able to parse generic metrics config elements" in {
      val settings = new ClusterMetricsSettings(system.settings.config)
      import settings._

      // Extension.
      MetricsDispatcher should ===(Dispatchers.DefaultDispatcherId)
      PeriodicTasksInitialDelay should ===(1.second)
      NativeLibraryExtractFolder should ===(System.getProperty("user.dir") + "/native")

      // Supervisor.
      SupervisorName should ===("cluster-metrics")
      SupervisorStrategyProvider should ===(classOf[ClusterMetricsStrategy].getName)
      SupervisorStrategyConfiguration should ===(
        ConfigFactory.parseString("loggingEnabled=true,maxNrOfRetries=3,withinTimeRange=3s"))

      // Collector.
      CollectorEnabled should ===(true)
      CollectorProvider should ===("")
      CollectorSampleInterval should ===(3.seconds)
      CollectorGossipInterval should ===(3.seconds)
      CollectorMovingAverageHalfLife should ===(12.seconds)
    }
  }
}

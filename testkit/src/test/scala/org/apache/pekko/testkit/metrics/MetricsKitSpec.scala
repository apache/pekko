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

package org.apache.pekko.testkit.metrics

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

class MetricsKitSpec extends AnyWordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with MetricsKit {

  import scala.concurrent.duration._

  override def metricsConfig = ConfigFactory.load()

  val KitKey = MetricKey.fromString("metrics-kit")

  after {
    clearMetrics()
  }

  override def afterAll(): Unit = {
    shutdownMetrics()
  }

  "MetricsKit" must {

    "allow measuring file descriptor usage" in {
      measureFileDescriptors(KitKey / "file-desc")

      registeredMetrics.count(_._1 contains "file-descriptor") should be > 0
    }

    "allow to measure time, on known number of operations" in {
      timedWithKnownOps(KitKey, ops = 10) {
        2 + 2
      }
    }

    "allow to measure average value using Gauge, given multiple values" in {
      val sizes = List(1L, 2L, 3L)

      val avg = averageGauge(KitKey / "avg-size")

      avg.add(sizes)
      avg.add(4)

      avg.getValue should equal(2.5)
    }

    "measure values in histogram" in {
      val maxMillis = 100.millis.toNanos
      val hist = hdrHistogram(KitKey / "hist", highestTrackableValue = maxMillis, 4, "ns")

      for {
        _ <- 1 to 11
        i <- 0L to 1579331
      } hist.update(i)

      hist.update(1579331)
      reportMetrics()
    }

    "fail with human readable error when the histogram overflowed" in {
      val maxMillis = 100.millis.toNanos
      val hist = hdrHistogram(KitKey / "hist", highestTrackableValue = maxMillis, 4, "ns")

      val ex = intercept[IllegalArgumentException] {
        hist.update(10.second.toNanos)
      }

      ex.getMessage should include("can not be stored in this histogram")
    }

  }

}

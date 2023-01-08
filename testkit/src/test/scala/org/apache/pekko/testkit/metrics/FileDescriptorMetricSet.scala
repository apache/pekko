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

package org.apache.pekko.testkit.metrics

import java.lang.management.{ ManagementFactory, OperatingSystemMXBean }
import java.util

import com.codahale.metrics.{ Gauge, Metric, MetricSet }
import com.codahale.metrics.MetricRegistry._
import com.codahale.metrics.jvm.FileDescriptorRatioGauge

import org.apache.pekko.util.ccompat.JavaConverters._

/**
 * MetricSet exposing number of open and maximum file descriptors used by the JVM process.
 */
private[pekko] class FileDescriptorMetricSet(os: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean)
    extends MetricSet {

  override def getMetrics: util.Map[String, Metric] = {
    Map[String, Metric](name("file-descriptors", "open") -> new Gauge[Long] {
        override def getValue: Long = invoke("getOpenFileDescriptorCount")
      },
      name("file-descriptors", "max") -> new Gauge[Long] {
        override def getValue: Long = invoke("getMaxFileDescriptorCount")
      }, name("file-descriptors", "ratio") -> new FileDescriptorRatioGauge(os)).asJava
  }

  private def invoke(name: String): Long = {
    val method = os.getClass.getDeclaredMethod(name)
    method.setAccessible(true)
    method.invoke(os).asInstanceOf[Long]
  }
}

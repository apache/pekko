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

import java.lang.invoke.{ MethodHandle, MethodHandles, MethodType }
import java.lang.management.{ ManagementFactory, OperatingSystemMXBean }
import java.util

import scala.jdk.CollectionConverters._

import com.codahale.metrics.{ Gauge, Metric, MetricSet }
import com.codahale.metrics.MetricRegistry._
import com.codahale.metrics.jvm.FileDescriptorRatioGauge

import com.sun.management.UnixOperatingSystemMXBean

/**
 * MetricSet exposing number of open and maximum file descriptors used by the JVM process.
 */
private[pekko] class FileDescriptorMetricSet(os: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean)
    extends MetricSet {

  override def getMetrics: util.Map[String, Metric] = {
    Map[String, Metric](name("file-descriptors", "open") -> new Gauge[Long] {
        override def getValue: Long = FileDescriptorMetricSet.openFileDescriptorCount(os)
      },
      name("file-descriptors", "max") -> new Gauge[Long] {
        override def getValue: Long = FileDescriptorMetricSet.maxFileDescriptorCount(os)
      }, name("file-descriptors", "ratio") -> new FileDescriptorRatioGauge(os)).asJava
  }
}

private object FileDescriptorMetricSet {
  private val longMethodType = MethodType.methodType(java.lang.Long.TYPE)
  private val invokerType = MethodType.methodType(classOf[AnyRef], classOf[AnyRef])
  private val lookup = MethodHandles.publicLookup()
  private val openFileDescriptorCountHandle = lookup
    .findVirtual(classOf[UnixOperatingSystemMXBean], "getOpenFileDescriptorCount", longMethodType)
    .asType(invokerType)
  private val maxFileDescriptorCountHandle = lookup
    .findVirtual(classOf[UnixOperatingSystemMXBean], "getMaxFileDescriptorCount", longMethodType)
    .asType(invokerType)

  private def openFileDescriptorCount(os: OperatingSystemMXBean): Long =
    invoke(openFileDescriptorCountHandle, os)

  private def maxFileDescriptorCount(os: OperatingSystemMXBean): Long =
    invoke(maxFileDescriptorCountHandle, os)

  private def invoke(handle: MethodHandle, os: OperatingSystemMXBean): Long = {
    val result: AnyRef = handle.invokeExact(os.asInstanceOf[AnyRef])
    result.asInstanceOf[Long]
  }
}

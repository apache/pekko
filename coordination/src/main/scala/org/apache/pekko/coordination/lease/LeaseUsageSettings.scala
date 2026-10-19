/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

import org.apache.pekko
import pekko.util.PrettyDuration._

/**
 * Note that if you define a custom lease name and have several Cluster Singletons or Cluster Sharding
 * entity types each one must have a unique lease name. If the lease name is undefined it will be derived
 * from ActorSystem name and other component names, but that may result in too long lease names.
 */
final class LeaseUsageSettings private[pekko] (
    val leaseImplementation: String,
    val leaseRetryInterval: FiniteDuration,
    val leaseName: String) {

  def this(leaseImplementation: String, leaseRetryInterval: FiniteDuration) =
    this(leaseImplementation, leaseRetryInterval, leaseName = "")

  def getLeaseRetryInterval(): java.time.Duration = leaseRetryInterval.toJava

  /**
   * Java API: The custom lease name, empty if the lease name is derived from the
   * ActorSystem name and other component names.
   */
  def getLeaseName(): String = leaseName

  /**
   * Define a custom lease name. Note that if you have several Cluster Singletons or Cluster Sharding
   * entity types each one must have a unique lease name. For Cluster Sharding the shard identifier
   * is appended to the custom lease name, so that each shard has its own lease.
   * If the lease name is undefined (empty) it will be derived from ActorSystem name and other
   * component names, but that may result in too long lease names.
   */
  def withLeaseName(name: String): LeaseUsageSettings =
    new LeaseUsageSettings(leaseImplementation, leaseRetryInterval, name)

  override def toString =
    if (leaseName.isEmpty) s"LeaseUsageSettings($leaseImplementation, ${leaseRetryInterval.pretty})"
    else s"LeaseUsageSettings($leaseImplementation, ${leaseRetryInterval.pretty}, $leaseName)"
}

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

import com.typesafe.config.Config

object LeaseSettings {
  def apply(config: Config, leaseName: String, ownerName: String): LeaseSettings = {
    new LeaseSettings(leaseName, ownerName, TimeoutSettings(config), config)
  }
}

final class LeaseSettings(
    val leaseName: String,
    val ownerName: String,
    val timeoutSettings: TimeoutSettings,
    val leaseConfig: Config) {

  def withTimeoutSettings(timeoutSettings: TimeoutSettings): LeaseSettings =
    new LeaseSettings(leaseName, ownerName, timeoutSettings, leaseConfig)

  override def toString = s"LeaseSettings($leaseName, $ownerName, $timeoutSettings)"
}

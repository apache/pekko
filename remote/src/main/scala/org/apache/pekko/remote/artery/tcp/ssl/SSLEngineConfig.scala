/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery.tcp.ssl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ccompat.JavaConverters._
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
 * INTERNAL API
 */
@InternalApi
private[tcp] class SSLEngineConfig(config: Config) {
  private[tcp] val SSLRandomNumberGenerator: String = config.getString("random-number-generator")

  private[tcp] val SSLProtocol: String = config.getString("protocol")
  private[tcp] val SSLEnabledAlgorithms: Set[String] =
    config.getStringList("enabled-algorithms").asScala.toSet
  private[tcp] val SSLRequireMutualAuthentication: Boolean = {
    if (config.hasPath("require-mutual-authentication"))
      config.getBoolean("require-mutual-authentication")
    else
      false
  }
  private[tcp] val HostnameVerification: Boolean = {
    if (config.hasPath("hostname-verification"))
      config.getBoolean("hostname-verification")
    else
      false
  }
  private[tcp] val SSLContextCacheTime: FiniteDuration =
    if (config.hasPath("ssl-context-cache-ttl"))
      config.getDuration("ssl-context-cache-ttl").toMillis.millis
    else
      1024.days // a lot of days (not Inf, because `Inf` is not a FiniteDuration
}

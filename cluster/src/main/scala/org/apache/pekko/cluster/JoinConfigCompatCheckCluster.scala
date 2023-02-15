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

package org.apache.pekko.cluster

import scala.collection.{ immutable => im }

import com.typesafe.config.Config

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.cluster.sbr.SplitBrainResolverProvider

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object JoinConfigCompatCheckCluster {
  private val DowningProviderPath = "pekko.cluster.downing-provider-class"
  private val SbrStrategyPath = "pekko.cluster.split-brain-resolver.active-strategy"
}

/**
 * INTERNAL API
 */
@InternalApi
final class JoinConfigCompatCheckCluster extends JoinConfigCompatChecker {
  import JoinConfigCompatCheckCluster._

  override def requiredKeys: im.Seq[String] = List(DowningProviderPath, SbrStrategyPath)

  override def check(toCheck: Config, actualConfig: Config): ConfigValidation = {
    val toCheckDowningProvider = toCheck.getString(DowningProviderPath)
    val actualDowningProvider = actualConfig.getString(DowningProviderPath)
    val downingProviderResult =
      if (toCheckDowningProvider == actualDowningProvider)
        Valid
      else
        JoinConfigCompatChecker.checkEquality(List(DowningProviderPath), toCheck, actualConfig)

    val sbrStrategyResult =
      if (toCheck.hasPath(SbrStrategyPath) && actualConfig.hasPath(SbrStrategyPath))
        JoinConfigCompatChecker.checkEquality(List(SbrStrategyPath), toCheck, actualConfig)
      else Valid

    downingProviderResult ++ sbrStrategyResult
  }
}

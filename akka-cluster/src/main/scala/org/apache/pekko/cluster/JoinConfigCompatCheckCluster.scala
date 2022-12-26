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

  private val AkkaSbrProviderClass = classOf[SplitBrainResolverProvider].getName
  private val LightbendSbrProviderClass = "com.lightbend.pekko.sbr.SplitBrainResolverProvider"
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
      if (toCheckDowningProvider == actualDowningProvider || Set(toCheckDowningProvider, actualDowningProvider) == Set(
          AkkaSbrProviderClass,
          LightbendSbrProviderClass))
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

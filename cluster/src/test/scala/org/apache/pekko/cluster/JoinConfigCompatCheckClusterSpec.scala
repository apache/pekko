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

package org.apache.pekko.cluster

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.testkit.PekkoSpec

class JoinConfigCompatCheckClusterSpec extends PekkoSpec {

  private val extSystem = system.asInstanceOf[ExtendedActorSystem]
  private val clusterSettings = new ClusterSettings(system.settings.config, system.name)
  private val joinConfigCompatChecker: JoinConfigCompatChecker =
    JoinConfigCompatChecker.load(extSystem, clusterSettings)

  // Corresponding to the check of InitJoin
  def checkInitJoin(oldConfig: Config, newConfig: Config): ConfigValidation = {
    // joiningNodeConfig only contains the keys that are required according to JoinConfigCompatChecker on this node
    val joiningNodeConfig = {
      val requiredNonSensitiveKeys =
        JoinConfigCompatChecker.removeSensitiveKeys(joinConfigCompatChecker.requiredKeys, clusterSettings)
      JoinConfigCompatChecker.filterWithKeys(requiredNonSensitiveKeys, newConfig)
    }

    val configWithoutSensitiveKeys = {
      val allowedConfigPaths =
        JoinConfigCompatChecker.removeSensitiveKeys(oldConfig, clusterSettings)
      // build a stripped down config instead where sensitive config paths are removed
      // we don't want any check to happen on those keys
      JoinConfigCompatChecker.filterWithKeys(allowedConfigPaths, oldConfig)
    }

    joinConfigCompatChecker.check(joiningNodeConfig, configWithoutSensitiveKeys)
  }

  // Corresponding to the check of InitJoinAck in SeedNodeProcess
  def checkInitJoinAck(oldConfig: Config, newConfig: Config): ConfigValidation = {
    // validates config coming from cluster against this node config
    val configCheckReply = {
      val nonSensitiveKeys = JoinConfigCompatChecker.removeSensitiveKeys(newConfig, clusterSettings)
      // Send back to joining node a subset of current configuration
      // containing the keys initially sent by the joining node minus
      // any sensitive keys as defined by this node configuration
      JoinConfigCompatChecker.filterWithKeys(nonSensitiveKeys, oldConfig)
    }
    joinConfigCompatChecker.check(configCheckReply, newConfig)
  }

  "JoinConfigCompatCheckCluster" must {
    "be valid when no downing-provider" in {
      val oldConfig = ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = ""
        """).withFallback(system.settings.config)
      val newConfig = ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = ""
        """).withFallback(system.settings.config)
      checkInitJoin(oldConfig, newConfig) should ===(Valid)
    }

    "be valid when same downing-provider" in {
      val oldConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        """).withFallback(system.settings.config)
      val newConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        """).withFallback(system.settings.config)
      checkInitJoin(oldConfig, newConfig) should ===(Valid)
    }

    "be invalid when different downing-provider" in {
      val oldConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.testkit.AutoDowning"
        """).withFallback(system.settings.config)
      val newConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        """).withFallback(system.settings.config)
      checkInitJoin(oldConfig, newConfig).getClass should ===(classOf[Invalid])
    }

    "be invalid when different sbr strategy" in {
      val oldConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        pekko.cluster.split-brain-resolver.active-strategy = keep-majority
        """).withFallback(system.settings.config)
      val newConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        pekko.cluster.split-brain-resolver.active-strategy = keep-oldest
        """).withFallback(system.settings.config)
      checkInitJoin(oldConfig, newConfig).getClass should ===(classOf[Invalid])
      checkInitJoinAck(oldConfig, newConfig).getClass should ===(classOf[Invalid])
    }

    "be valid when equivalent downing-provider (akka/pekko mixed cluster)" in {
      val oldConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        """).withFallback(system.settings.config)
      val newConfig =
        ConfigFactory.parseString("""
        akka.cluster.downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
        akka.version = "2.6.21"
        """)
      checkInitJoin(oldConfig, ConfigUtil.changeAkkaToPekkoConfig(newConfig)) should ===(Valid)
    }

    "be invalid when not equivalent downing-provider (akka/pekko mixed cluster)" in {
      val oldConfig =
        ConfigFactory.parseString("""
        pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.testkit.AutoDowning"
        """).withFallback(system.settings.config)
      val newConfig =
        ConfigFactory.parseString("""
        akka.cluster.downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
        akka.version = "2.6.21"
        """)
      checkInitJoin(oldConfig, ConfigUtil.changeAkkaToPekkoConfig(newConfig)).getClass should ===(classOf[Invalid])
    }

  }
}

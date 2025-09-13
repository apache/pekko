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

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.actor.{ Address, ExtendedActorSystem }
import pekko.testkit.{ EventFilter, ImplicitSender, PekkoSpec }

import com.typesafe.config.{ Config, ConfigFactory }

object ClusterLogSpec {
  val config = """
    pekko.cluster {
      downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
      testkit.auto-down-unreachable-after = 0s
      publish-stats-interval = 0 s # always, when it happens
      failure-detector.implementation-class = org.apache.pekko.cluster.FailureDetectorPuppet
    }
    pekko.actor.provider = "cluster"
    pekko.remote.log-remote-lifecycle-events = off
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
    pekko.loglevel = "INFO"
    pekko.loggers = ["org.apache.pekko.testkit.TestEventListener"]
    """

}

abstract class ClusterLogSpec(config: Config) extends PekkoSpec(config) with ImplicitSender {

  def this(s: String) = this(ConfigFactory.parseString(s))

  protected val selfAddress: Address = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress

  protected val upLogMessage = "event MemberUp"

  protected val downLogMessage = "event MemberDowned"

  protected val cluster = Cluster(system)

  protected def clusterView: ClusterReadView = cluster.readView

  protected def awaitUp(): Unit = {
    awaitCond(clusterView.isSingletonCluster)
    clusterView.self.address should ===(selfAddress)
    clusterView.members.map(_.address) should ===(Set(selfAddress))
    awaitAssert(clusterView.status should ===(MemberStatus.Up))
  }

  /** The expected log info pattern to intercept after a `cluster.join`. */
  protected def join(expected: String): Unit =
    EventFilter.info(occurrences = 1, pattern = expected).intercept(cluster.join(selfAddress))

  /** The expected log info pattern to intercept after a `cluster.down`. */
  protected def down(expected: String): Unit =
    EventFilter.info(occurrences = 1, pattern = expected).intercept(cluster.down(selfAddress))
}

class ClusterLogDefaultSpec extends ClusterLogSpec(ClusterLogSpec.config) {

  "A Cluster" must {

    "Log a message when becoming and stopping being a leader" in {
      cluster.settings.LogInfo should ===(true)
      cluster.settings.LogInfoVerbose should ===(false)
      join("is the new leader")
      awaitUp()
      down("is no longer leader")
    }
  }
}

class ClusterLogVerboseDefaultSpec extends ClusterLogSpec(ConfigFactory.parseString(ClusterLogSpec.config)) {

  "A Cluster" must {

    "not log verbose cluster events by default" in {
      cluster.settings.LogInfoVerbose should ===(false)
      intercept[AssertionError](join(upLogMessage))
      awaitUp()
      intercept[AssertionError](down(downLogMessage))
    }
  }
}

class ClusterLogVerboseEnabledSpec
    extends ClusterLogSpec(
      ConfigFactory
        .parseString("pekko.cluster.log-info-verbose = on")
        .withFallback(ConfigFactory.parseString(ClusterLogSpec.config))) {

  "A Cluster" must {

    "log verbose cluster events when 'log-info-verbose = on'" in {
      cluster.settings.LogInfoVerbose should ===(true)
      join(upLogMessage)
      awaitUp()
      down(downLogMessage)
    }
  }
}

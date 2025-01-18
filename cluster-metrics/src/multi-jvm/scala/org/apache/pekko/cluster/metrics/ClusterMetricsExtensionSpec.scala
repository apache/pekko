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

package org.apache.pekko.cluster.metrics

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.cluster.MemberStatus
import pekko.cluster.MultiNodeClusterSpec
import pekko.remote.testkit.MultiNodeConfig

trait ClusterMetricsCommonConfig extends MultiNodeConfig {
  import ConfigFactory._

  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")
  val node4 = role("node-4")
  val node5 = role("node-5")

  def nodeList = Seq(node1, node2, node3, node4, node5)

  // Extract individual sigar library for every node.
  nodeList.foreach { role =>
    nodeConfig(role) {
      parseString(s"pekko.cluster.metrics.native-library-extract-folder=$${user.dir}/target/native/" + role.name)
    }
  }

  // Enable metrics extension in pekko-cluster-metrics.
  def enableMetricsExtension = parseString("""
    pekko.extensions=["org.apache.pekko.cluster.metrics.ClusterMetricsExtension"]
    pekko.cluster.metrics.collector.enabled = on
    """)

  // Disable metrics extension in pekko-cluster-metrics.
  def disableMetricsExtension = parseString("""
    pekko.extensions=["org.apache.pekko.cluster.metrics.ClusterMetricsExtension"]
    pekko.cluster.metrics.collector.enabled = off
    """)

  // Activate slf4j logging along with test listener.
  def customLogging = parseString("""
      pekko.loggers=["org.apache.pekko.testkit.TestEventListener","org.apache.pekko.event.slf4j.Slf4jLogger"]
      pekko.logger-startup-timeout = 15s
    """)
}

object ClusterMetricsDisabledConfig extends ClusterMetricsCommonConfig {

  commonConfig {
    Seq(
      customLogging,
      disableMetricsExtension,
      debugConfig(on = false),
      MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet).reduceLeft(_.withFallback(_))
  }
}

object ClusterMetricsEnabledConfig extends ClusterMetricsCommonConfig {

  commonConfig {
    Seq(
      customLogging,
      enableMetricsExtension,
      debugConfig(on = false),
      MultiNodeClusterSpec.clusterConfigWithFailureDetectorPuppet).reduceLeft(_.withFallback(_))
  }

}

class ClusterMetricsEnabledMultiJvmNode1 extends ClusterMetricsEnabledSpec
class ClusterMetricsEnabledMultiJvmNode2 extends ClusterMetricsEnabledSpec
class ClusterMetricsEnabledMultiJvmNode3 extends ClusterMetricsEnabledSpec
class ClusterMetricsEnabledMultiJvmNode4 extends ClusterMetricsEnabledSpec
class ClusterMetricsEnabledMultiJvmNode5 extends ClusterMetricsEnabledSpec

abstract class ClusterMetricsEnabledSpec
    extends MultiNodeClusterSpec(ClusterMetricsEnabledConfig)
    with RedirectLogging {
  import ClusterMetricsEnabledConfig._

  def isSigar(collector: MetricsCollector): Boolean = collector.isInstanceOf[SigarMetricsCollector]

  def saveApplicationConf(): Unit = {
    import java.io.File
    import java.io.PrintWriter
    val conf = cluster.system.settings.config
    val text = conf.root.render
    val file = new File(s"target/${myself.name}_application.conf")
    Some(new PrintWriter(file)).map { p =>
      p.write(text); p.close
    }
  }

  saveApplicationConf()

  val metricsView = new ClusterMetricsView(cluster.system)

  "Cluster metrics" must {
    "periodically collect metrics on each node, publish to the event stream, " +
    "and gossip metrics around the node ring" in within(60.seconds) {
      awaitClusterUp(roles: _*)
      enterBarrier("cluster-started")
      awaitAssert(clusterView.members.count(_.status == MemberStatus.Up) should ===(roles.size))
      // TODO ensure same contract
      // awaitAssert(clusterView.clusterMetrics.size should ===(roles.size))
      awaitAssert(metricsView.clusterMetrics.size should ===(roles.size))
      val collector = MetricsCollector(cluster.system)
      collector.sample().metrics.size should be > 3
      enterBarrier("after")
    }
    "reflect the correct number of node metrics in cluster view" in within(30.seconds) {
      runOn(node2) {
        cluster.leave(node1)
      }
      enterBarrier("first-left")
      runOn(node2, node3, node4, node5) {
        markNodeAsUnavailable(node1)
        // TODO ensure same contract
        // awaitAssert(clusterView.clusterMetrics.size should ===(roles.size - 1))
        awaitAssert(metricsView.clusterMetrics.size should ===(roles.size - 1))
      }
      enterBarrier("finished")
    }
  }
}

class ClusterMetricsDisabledMultiJvmNode1 extends ClusterMetricsDisabledSpec
class ClusterMetricsDisabledMultiJvmNode2 extends ClusterMetricsDisabledSpec
class ClusterMetricsDisabledMultiJvmNode3 extends ClusterMetricsDisabledSpec
class ClusterMetricsDisabledMultiJvmNode4 extends ClusterMetricsDisabledSpec
class ClusterMetricsDisabledMultiJvmNode5 extends ClusterMetricsDisabledSpec

abstract class ClusterMetricsDisabledSpec
    extends MultiNodeClusterSpec(ClusterMetricsDisabledConfig)
    with RedirectLogging {

  val metricsView = new ClusterMetricsView(cluster.system)

  "Cluster metrics" must {
    "not collect metrics, not publish metrics events, and not gossip metrics" in {
      awaitClusterUp(roles: _*)
      // TODO ensure same contract
      // clusterView.clusterMetrics.size should ===(0)
      metricsView.clusterMetrics.size should ===(0)
      ClusterMetricsExtension(system).subscribe(testActor)
      expectNoMessage()
      // TODO ensure same contract
      // clusterView.clusterMetrics.size should ===(0)
      metricsView.clusterMetrics.size should ===(0)
      enterBarrier("after")
    }
  }
}

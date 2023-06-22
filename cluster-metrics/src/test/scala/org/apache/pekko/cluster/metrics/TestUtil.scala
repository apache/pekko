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

import java.io.Closeable
import java.util.logging.LogManager

import scala.language.postfixOps

import org.scalatestplus.mockito.MockitoSugar
import org.slf4j.bridge.SLF4JBridgeHandler

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.Deploy
import pekko.actor.ExtendedActorSystem
import pekko.actor.PoisonPill
import pekko.actor.Props
import pekko.dispatch.Dispatchers
import pekko.dispatch.RequiresMessageQueue
import pekko.dispatch.UnboundedMessageQueueSemantics
import pekko.remote.RARP
import pekko.testkit.PekkoSpec

/**
 * Redirect different logging sources to SLF4J.
 */
trait RedirectLogging {

  def redirectLogging(): Unit = {
    // Redirect JUL to SLF4J.
    LogManager.getLogManager().reset()
    SLF4JBridgeHandler.install()
  }

  redirectLogging()

}

/**
 * Provide sigar library from `project/target` location.
 */
case class SimpleSigarProvider(location: String = "native") extends SigarProvider {
  def extractFolder = s"${System.getProperty("user.dir")}/target/$location"
}

/**
 * Provide sigar library as static mock.
 */
case class MockitoSigarProvider(
    pid: Long = 123,
    loadAverage: Array[Double] = Array(0.7, 0.3, 0.1),
    cpuCombined: Double = 0.5,
    cpuStolen: Double = 0.2,
    steps: Int = 5)
    extends SigarProvider
    with MockitoSugar {

  import org.hyperic.sigar._
  import org.mockito.Mockito._

  /** Not used. */
  override def extractFolder = ???

  /** Generate monotonic array from 0 to value. */
  def increase(value: Double): Array[Double] = {
    val delta = value / steps
    (0 to steps).map { _ * delta } toArray
  }

  /** Sigar mock instance. */
  override def verifiedSigarInstance = {

    // Note "thenReturn(0)" invocation is consumed in collector construction.

    val cpuPerc = mock[CpuPerc]
    when(cpuPerc.getCombined).thenReturn(0.0, increase(cpuCombined): _*)
    when(cpuPerc.getStolen).thenReturn(0.0, increase(cpuStolen): _*)

    val sigar = mock[SigarProxy]
    when(sigar.getPid).thenReturn(pid)
    when(sigar.getLoadAverage).thenReturn(loadAverage) // Constant.
    when(sigar.getCpuPerc).thenReturn(cpuPerc) // Increasing.

    sigar
  }
}

/**
 * Used when testing metrics without full cluster
 *
 * TODO change factory after https://github.com/akka/akka/issues/16369
 */
trait MetricsCollectorFactory { this: PekkoSpec =>
  import MetricsConfig._
  import org.hyperic.sigar.Sigar

  private def extendedActorSystem = system.asInstanceOf[ExtendedActorSystem]

  def selfAddress = extendedActorSystem.provider.rootPath.address

  def createMetricsCollector: MetricsCollector =
    try {
      new SigarMetricsCollector(selfAddress, defaultDecayFactor, new Sigar())
      // new SigarMetricsCollector(selfAddress, defaultDecayFactor, SimpleSigarProvider().createSigarInstance)
    } catch {
      case e: Throwable =>
        log.warning("Sigar failed to load. Using JMX. Reason: " + e.toString)
        new JmxMetricsCollector(selfAddress, defaultDecayFactor)
    }

  /** Create JMX collector. */
  def collectorJMX: MetricsCollector =
    new JmxMetricsCollector(selfAddress, defaultDecayFactor)

  /** Create Sigar collector. Rely on java agent injection. */
  def collectorSigarDefault: MetricsCollector =
    new SigarMetricsCollector(selfAddress, defaultDecayFactor, new Sigar())

  /** Create Sigar collector. Rely on sigar-loader provisioner. */
  def collectorSigarProvision: MetricsCollector =
    new SigarMetricsCollector(selfAddress, defaultDecayFactor, SimpleSigarProvider().createSigarInstance)

  /** Create Sigar collector. Rely on static sigar library mock. */
  def collectorSigarMockito: MetricsCollector =
    new SigarMetricsCollector(selfAddress, defaultDecayFactor, MockitoSigarProvider().createSigarInstance)

  def isSigar(collector: MetricsCollector): Boolean = collector.isInstanceOf[SigarMetricsCollector]
}

/**
 */
class MockitoSigarMetricsCollector(system: ActorSystem)
    extends SigarMetricsCollector(
      Address(if (RARP(system).provider.remoteSettings.Artery.Enabled) "pekko" else "pekko.tcp", system.name),
      MetricsConfig.defaultDecayFactor,
      MockitoSigarProvider().createSigarInstance) {}

/**
 * Metrics test configurations.
 */
object MetricsConfig {

  val defaultDecayFactor = 2.0 / (1 + 10)

  /** Test w/o cluster, with collection enabled. */
  val defaultEnabled = """
    pekko.cluster.metrics {
      collector {
        enabled = on
        sample-interval = 1s
        gossip-interval = 1s
      }
    }
    pekko.actor.provider = remote
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
  """

  /** Test w/o cluster, with collection disabled. */
  val defaultDisabled = """
    pekko.cluster.metrics {
      collector {
        enabled = off
      }
    }
    pekko.actor.provider = remote
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
  """

  /** Test in cluster, with manual collection activation, collector mock, fast. */
  val clusterSigarMock = """
    pekko.cluster.metrics {
      periodic-tasks-initial-delay = 100ms
      collector {
        enabled = off
        sample-interval = 200ms
        gossip-interval = 200ms
        provider = "org.apache.pekko.cluster.metrics.MockitoSigarMetricsCollector"
        fallback = false
      }
    }
    pekko.actor.provider = "cluster"
    pekko.remote.classic.netty.tcp.port = 0
    pekko.remote.artery.canonical.port = 0
  """
}

/**
 * Current cluster metrics, updated periodically via event bus.
 */
class ClusterMetricsView(system: ExtendedActorSystem) extends Closeable {

  val extension = ClusterMetricsExtension(system)

  /** Current cluster metrics, updated periodically via event bus. */
  @volatile
  private var currentMetricsSet: Set[NodeMetrics] = Set.empty

  /** Collected cluster metrics history. */
  @volatile
  private var collectedMetricsList: List[Set[NodeMetrics]] = List.empty

  /** Create actor that subscribes to the cluster eventBus to update current read view state. */
  private val eventBusListener: ActorRef = {
    system.systemActorOf(
      Props(new Actor with ActorLogging with RequiresMessageQueue[UnboundedMessageQueueSemantics] {
        override def preStart(): Unit = extension.subscribe(self)
        override def postStop(): Unit = extension.unsubscribe(self)
        def receive = {
          case ClusterMetricsChanged(nodes) =>
            currentMetricsSet = nodes
            collectedMetricsList = nodes :: collectedMetricsList
          case _ =>
          // Ignore.
        }
      }).withDispatcher(Dispatchers.DefaultDispatcherId).withDeploy(Deploy.local),
      name = "metrics-event-bus-listener")
  }

  /** Current cluster metrics. */
  def clusterMetrics: Set[NodeMetrics] = currentMetricsSet

  /** Collected cluster metrics history. */
  def metricsHistory: List[Set[NodeMetrics]] = collectedMetricsList

  /** Unsubscribe from cluster events. */
  def close(): Unit = eventBusListener ! PoisonPill

}

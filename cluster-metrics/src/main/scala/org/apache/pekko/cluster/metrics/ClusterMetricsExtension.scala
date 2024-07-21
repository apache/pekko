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

import scala.collection.immutable

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.Deploy
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.actor.Props
import pekko.actor.SupervisorStrategy
import pekko.event.Logging
import pekko.event.LoggingAdapter

/**
 * Cluster metrics extension.
 *
 * Cluster metrics is primarily for load-balancing of nodes. It controls metrics sampling
 * at a regular frequency, prepares highly variable data for further analysis by other entities,
 * and publishes the latest cluster metrics data around the node ring and local eventStream
 * to assist in determining the need to redirect traffic to the least-loaded nodes.
 *
 * Metrics sampling is delegated to the [[MetricsCollector]].
 *
 * Smoothing of the data for each monitored process is delegated to the
 * [[EWMA]] for exponential weighted moving average.
 */
class ClusterMetricsExtension(system: ExtendedActorSystem) extends Extension {

  /**
   * Metrics extension configuration.
   */
  val settings = ClusterMetricsSettings(system.settings.config)
  import settings._

  /**
   * INTERNAL API
   *
   * Supervision strategy.
   */
  private[metrics] val strategy = system.dynamicAccess
    .createInstanceFor[SupervisorStrategy](
      SupervisorStrategyProvider,
      immutable.Seq(classOf[Config] -> SupervisorStrategyConfiguration))
    .getOrElse {
      val log: LoggingAdapter = Logging(system, classOf[ClusterMetricsExtension])
      log.error(s"Configured strategy provider $SupervisorStrategyProvider failed to load, using default ${classOf[
          ClusterMetricsStrategy].getName}.")
      new ClusterMetricsStrategy(SupervisorStrategyConfiguration)
    }

  /**
   * Supervisor actor.
   * Accepts subtypes of [[CollectionControlMessage]]s to manage metrics collection at runtime.
   */
  val supervisor = system.systemActorOf(
    Props(classOf[ClusterMetricsSupervisor]).withDispatcher(MetricsDispatcher).withDeploy(Deploy.local),
    SupervisorName)

  /**
   * Subscribe user metrics listener actor unto [[ClusterMetricsEvent]]
   * events published by extension on the system event bus.
   */
  def subscribe(metricsListener: ActorRef): Unit =
    system.eventStream.subscribe(metricsListener, classOf[ClusterMetricsEvent])

  /**
   * Unsubscribe user metrics listener actor from [[ClusterMetricsEvent]]
   * events published by extension on the system event bus.
   */
  def unsubscribe(metricsListenter: ActorRef): Unit =
    system.eventStream.unsubscribe(metricsListenter, classOf[ClusterMetricsEvent])

}

/**
 * Cluster metrics extension provider.
 */
object ClusterMetricsExtension extends ExtensionId[ClusterMetricsExtension] with ExtensionIdProvider {
  override def lookup = ClusterMetricsExtension
  override def get(system: ActorSystem): ClusterMetricsExtension = super.get(system)
  override def get(system: ClassicActorSystemProvider): ClusterMetricsExtension = super.get(system)
  override def createExtension(system: ExtendedActorSystem): ClusterMetricsExtension =
    new ClusterMetricsExtension(system)
}

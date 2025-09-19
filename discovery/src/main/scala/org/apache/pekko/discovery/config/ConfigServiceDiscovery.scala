/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.discovery.config

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.{ Config, ConfigUtil }

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.discovery.{ Lookup, ServiceDiscovery }
import pekko.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import pekko.event.Logging
import scala.jdk.CollectionConverters._

/**
 * INTERNAL API
 */
@InternalApi
private object ConfigServicesParser {
  def parse(config: Config): Map[String, Resolved] = {
    val byService = config
      .root()
      .entrySet()
      .asScala
      .map { en =>
        (en.getKey, config.getConfig(ConfigUtil.quoteString(en.getKey)))
      }
      .toMap

    byService.map {
      case (serviceName, full) =>
        val endpoints = full.getConfigList("endpoints").asScala.toList
        val resolvedTargets = endpoints.map { c =>
          val host = c.getString("host")
          val port = if (c.hasPath("port")) Some(c.getInt("port")) else None
          ResolvedTarget(host = host, port = port, address = None)
        }
        (serviceName, Resolved(serviceName, resolvedTargets))
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] class ConfigServiceDiscovery(system: ExtendedActorSystem) extends ServiceDiscovery {

  private val log = Logging(system, classOf[ConfigServiceDiscovery])

  private val resolvedServices = ConfigServicesParser.parse(
    system.settings.config.getConfig(system.settings.config.getString("pekko.discovery.config.services-path")))

  log.debug("Config discovery serving: {}", resolvedServices)

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    Future.successful(resolvedServices.getOrElse(lookup.serviceName, Resolved(lookup.serviceName, Nil)))
  }

}

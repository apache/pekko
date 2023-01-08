/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.discovery.aggregate

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.discovery.{ Discovery, Lookup, ServiceDiscovery }
import pekko.discovery.ServiceDiscovery.Resolved
import pekko.discovery.aggregate.AggregateServiceDiscovery.Methods
import pekko.dispatch.MessageDispatcher
import pekko.event.Logging
import pekko.util.Helpers.Requiring
import pekko.util.ccompat.JavaConverters._

/**
 * INTERNAL API
 */
@InternalApi
private final class AggregateServiceDiscoverySettings(config: Config) {

  val discoveryMethods = config
    .getStringList("discovery-methods")
    .asScala
    .toList
    .requiring(_.nonEmpty, "At least one discovery method should be specified")

}

/**
 * INTERNAL API
 */
@InternalApi
private object AggregateServiceDiscovery {
  type Methods = List[(String, ServiceDiscovery)]
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class AggregateServiceDiscovery(system: ExtendedActorSystem) extends ServiceDiscovery {

  private val log = Logging(system, classOf[AggregateServiceDiscovery])

  private val settings =
    new AggregateServiceDiscoverySettings(system.settings.config.getConfig("pekko.discovery.aggregate"))

  private val methods = {
    val serviceDiscovery = Discovery(system)
    settings.discoveryMethods.map(mech => (mech, serviceDiscovery.loadServiceDiscovery(mech)))
  }
  private implicit val ec: MessageDispatcher = system.dispatchers.internalDispatcher

  /**
   * Each discovery method is given the resolveTimeout rather than reducing it each time between methods.
   */
  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    resolve(methods, lookup, resolveTimeout)

  private def resolve(sds: Methods, query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    sds match {
      case (method, next) :: Nil =>
        log.debug("Looking up [{}] with [{}]", query, method)
        next.lookup(query, resolveTimeout)
      case (method, next) :: tail =>
        log.debug("Looking up [{}] with [{}]", query, method)
        // If nothing comes back then try the next one
        next
          .lookup(query, resolveTimeout)
          .flatMap { resolved =>
            if (resolved.addresses.isEmpty) {
              log.debug("Method[{}] returned no ResolvedTargets, trying next", query)
              resolve(tail, query, resolveTimeout)
            } else
              Future.successful(resolved)
          }
          .recoverWith {
            case NonFatal(t) =>
              log.error(t, "[{}] Service discovery failed. Trying next discovery method", method)
              resolve(tail, query, resolveTimeout)
          }
      case Nil =>
        // this is checked in `discoveryMethods`, but silence compiler warning
        throw new IllegalStateException("At least one discovery method should be specified")
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, Deploy, Props }
import pekko.dispatch.{ RequiresMessageQueue, UnboundedMessageQueueSemantics }
import pekko.routing.FromConfig

final class SimpleDnsManager(val ext: DnsExt)
    extends Actor
    with RequiresMessageQueue[UnboundedMessageQueueSemantics]
    with ActorLogging {

  import context._

  private val resolver = actorOf(
    FromConfig.props(
      Props(ext.provider.actorClass, ext.cache, ext.Settings.ResolverConfig)
        .withDeploy(Deploy.local)
        .withDispatcher(ext.Settings.Dispatcher)),
    ext.Settings.Resolver)

  private val cacheCleanup = ext.cache match {
    case cleanup: PeriodicCacheCleanup => Some(cleanup)
    case _                             => None
  }

  private val cleanupTimer = cacheCleanup.map { _ =>
    val interval = Duration(
      ext.Settings.ResolverConfig.getDuration("cache-cleanup-interval", TimeUnit.MILLISECONDS),
      TimeUnit.MILLISECONDS)
    system.scheduler.scheduleWithFixedDelay(interval, interval, self, SimpleDnsManager.CacheCleanup)
  }

  // the inet resolver supports the old and new DNS APIs
  override def receive: Receive = {
    case m: dns.DnsProtocol.Resolve =>
      log.debug("Resolution request for {} from {}", m.name, sender())
      resolver.forward(m)

    case SimpleDnsManager.CacheCleanup =>
      cacheCleanup.foreach(_.cleanup())
  }

  override def postStop(): Unit = {
    cleanupTimer.foreach(_.cancel())
  }
}

object SimpleDnsManager {
  private case object CacheCleanup
}

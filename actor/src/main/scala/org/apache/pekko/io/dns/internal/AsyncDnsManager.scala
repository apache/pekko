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

package org.apache.pekko.io.dns.internal

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, ActorRefFactory, Deploy, ExtendedActorSystem, Props, Timers }
import pekko.annotation.InternalApi
import pekko.dispatch.{ RequiresMessageQueue, UnboundedMessageQueueSemantics }
import pekko.io.{ Dns, DnsExt, DnsProvider }
import pekko.io.PeriodicCacheCleanup
import pekko.io.dns.{ DnsProtocol, DnsSettings }
import pekko.io.dns.internal.AsyncDnsManager.CacheCleanup
import pekko.routing.FromConfig
import pekko.util.Timeout

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object AsyncDnsManager {
  private case object CacheCleanup

  case object GetCache
}

/**
 * INTERNAL API
 */
@InternalApi
private[io] final class AsyncDnsManager(
    name: String,
    system: ExtendedActorSystem,
    resolverConfig: Config,
    cache: Dns,
    dispatcher: String,
    provider: DnsProvider)
    extends Actor
    with RequiresMessageQueue[UnboundedMessageQueueSemantics]
    with ActorLogging
    with Timers {

  /**
   * Ctr expected by the DnsExt for all DnsMangers
   */
  def this(ext: DnsExt) =
    this(
      ext.Settings.Resolver,
      ext.system,
      ext.Settings.ResolverConfig,
      ext.cache,
      ext.Settings.Dispatcher,
      ext.provider)

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  val settings = new DnsSettings(system, resolverConfig)
  implicit val timeout: Timeout = Timeout(settings.ResolveTimeout)

  private val resolver = {
    val props: Props = FromConfig.props(
      Props(provider.actorClass, settings, cache,
        (factory: ActorRefFactory, dns: List[InetSocketAddress]) => {
          dns.map(ns => factory.actorOf(Props(new DnsClient(ns))))
        }).withDeploy(Deploy.local).withDispatcher(dispatcher))
    context.actorOf(props, name)
  }

  private val cacheCleanup = cache match {
    case cleanup: PeriodicCacheCleanup => Some(cleanup)
    case _                             => None
  }

  override def preStart(): Unit = {
    cacheCleanup.foreach { _ =>
      val interval =
        Duration(resolverConfig.getDuration("cache-cleanup-interval", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
      timers.startTimerWithFixedDelay(CacheCleanup, CacheCleanup, interval)
    }
  }

  override def receive: Receive = {
    case r: DnsProtocol.Resolve =>
      log.debug("Resolution request for {} {} from {}", r.name, r.requestType, sender())
      resolver.forward(r)

    case CacheCleanup =>
      cacheCleanup.foreach(_.cleanup())

    case AsyncDnsManager.GetCache =>
      sender() ! cache

  }
}

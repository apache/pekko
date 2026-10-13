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

package org.apache.pekko.cluster.sharding

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.annotation.InternalApi
import pekko.cluster.Cluster
import pekko.cluster.MemberStatus
import pekko.event.Logging
import pekko.pattern.AskTimeoutException
import pekko.pattern.ask
import pekko.util.Timeout

import com.typesafe.config.Config

/**
 * Internal API
 */
@InternalApi
private[pekko] object ClusterShardingHealthCheckSettings {
  def apply(config: Config): ClusterShardingHealthCheckSettings =
    new ClusterShardingHealthCheckSettings(
      config.getStringList("names").asScala.toSet,
      config.getDuration("timeout").toScala,
      config.getDuration("disabled-after").toScala)
}

final class ClusterShardingHealthCheckSettings(
    val names: Set[String],
    val timeout: FiniteDuration,
    val disableAfter: FiniteDuration) {

  // for binary backwards compatibility
  @deprecated("Use full constructor", "2.0.0")
  def this(names: Set[String], timeout: FiniteDuration) = this(names, timeout, 10.seconds)
}

private object ClusterShardingHealthCheck {
  val Success = Future.successful(true)
}

/**
 * INTERNAL API (ctr)
 */
final class ClusterShardingHealthCheck private[pekko] (
    system: ActorSystem,
    settings: ClusterShardingHealthCheckSettings,
    shardRegion: String => ActorRef)
    extends (() => Future[Boolean]) {

  private val log = Logging(system, classOf[ClusterShardingHealthCheck])

  def this(system: ActorSystem) =
    this(
      system,
      ClusterShardingHealthCheckSettings(system.settings.config.getConfig("pekko.cluster.sharding.healthcheck")),
      name => ClusterSharding(system).shardRegion(name))

  private implicit val timeout: Timeout = settings.timeout
  private implicit val ec: ExecutionContext = system.dispatchers.internalDispatcher

  // Once the check has passed it always does
  @volatile private var registered = false
  @volatile private var startedTimestamp = 0L

  private def isMemberUp(): Boolean = {
    val memberStatus = Cluster(system).selfMember.status
    memberStatus != MemberStatus.Joining && memberStatus != MemberStatus.Removed
  }

  override def apply(): Future[Boolean] = {
    if (settings.names.isEmpty || registered) {
      ClusterShardingHealthCheck.Success
    } else if (startedTimestamp != 0L &&
      System
        .currentTimeMillis() > startedTimestamp + settings.disableAfter.toMillis) {
      ClusterShardingHealthCheck.Success
    } else {
      if (startedTimestamp == 0 && isMemberUp())
        startedTimestamp = System.currentTimeMillis()

      Future
        .traverse(settings.names) { name =>
          shardRegion(name) // this can throw if shard region not registered and it'll fail the check
            .ask(ShardRegion.GetShardRegionStatus)
            .mapTo[ShardRegion.ShardRegionStatus]
        }
        .map { allResponses =>
          val allRegistered = allResponses.forall(_.registeredWithCoordinator)
          if (!allRegistered && log.isInfoEnabled) {
            log.info(
              "Not all shard regions have registered with coordinator. Still to register: [{}]",
              allResponses
                .collect {
                  case response if !response.registeredWithCoordinator => response.typeName
                }
                .mkString(","))
          }
          if (allRegistered) {
            registered = true
          }
          allRegistered
        }
        .recover {
          case _: AskTimeoutException =>
            if (log.isDebugEnabled) {
              log.debug(
                "Shard regions [{}] did not respond in time. Failing health check.",
                settings.names.mkString(","))
            }
            false
        }
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.external.internal

import java.util.concurrent.CompletionStage

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.AddressFromURIString
import pekko.annotation.InternalApi
import pekko.cluster.ddata.DistributedData
import pekko.cluster.ddata.LWWMap
import pekko.cluster.ddata.Replicator.Get
import pekko.cluster.ddata.Replicator.GetFailure
import pekko.cluster.ddata.Replicator.GetSuccess
import pekko.cluster.ddata.Replicator.NotFound
import pekko.cluster.ddata.Replicator.ReadMajority
import pekko.cluster.ddata.Replicator.Update
import pekko.cluster.ddata.Replicator.UpdateSuccess
import pekko.cluster.ddata.Replicator.UpdateTimeout
import pekko.cluster.ddata.Replicator.WriteLocal
import pekko.cluster.ddata.SelfUniqueAddress
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.cluster.sharding.external.ClientTimeoutException
import pekko.cluster.sharding.external.ExternalShardAllocationStrategy
import pekko.cluster.sharding.external.ExternalShardAllocationStrategy.ShardLocation
import pekko.cluster.sharding.external.ShardLocations
import pekko.dispatch.MessageDispatcher
import pekko.event.Logging
import pekko.pattern.ask
import pekko.util.FutureConverters._
import pekko.util.JavaDurationConverters._
import pekko.util.PrettyDuration._
import pekko.util.Timeout
import pekko.util.ccompat.JavaConverters._

/**
 * INTERNAL API
 */
@InternalApi
final private[external] class ExternalShardAllocationClientImpl(system: ActorSystem, typeName: String)
    extends pekko.cluster.sharding.external.scaladsl.ExternalShardAllocationClient
    with pekko.cluster.sharding.external.javadsl.ExternalShardAllocationClient {

  private val log = Logging(system, classOf[ExternalShardAllocationClientImpl])

  private val replicator: ActorRef = DistributedData(system).replicator
  private val self: SelfUniqueAddress = DistributedData(system).selfUniqueAddress

  private val timeout =
    system.settings.config
      .getDuration("pekko.cluster.sharding.external-shard-allocation-strategy.client-timeout")
      .asScala
  private implicit val askTimeout: Timeout = Timeout(timeout * 2)
  private implicit val ec: MessageDispatcher = system.dispatchers.internalDispatcher

  private val Key = ExternalShardAllocationStrategy.ddataKey(typeName)

  override def updateShardLocation(shard: ShardId, location: Address): Future[Done] = {
    log.debug("updateShardLocation {} {} key {}", shard, location, Key)
    (replicator ? Update(Key, LWWMap.empty[ShardId, String], WriteLocal, None) { existing =>
      existing.put(self, shard, location.toString)
    }).flatMap {
      case UpdateSuccess(_, _) => Future.successful(Done)
      case UpdateTimeout =>
        Future.failed(new ClientTimeoutException(s"Unable to update shard location after ${timeout.duration.pretty}"))
      case _ => throw new IllegalArgumentException() // compiler exhaustiveness check pleaser
    }
  }

  override def setShardLocation(shard: ShardId, location: Address): CompletionStage[Done] =
    updateShardLocation(shard, location).asJava

  override def shardLocations(): Future[ShardLocations] =
    (replicator ? Get(Key, ReadMajority(timeout)))
      .flatMap {
        case success @ GetSuccess(`Key`, _) =>
          Future.successful(
            success.get(Key).entries.transform((_, asStr) => ShardLocation(AddressFromURIString(asStr))))
        case NotFound(_, _) =>
          Future.successful(Map.empty[ShardId, ShardLocation])
        case GetFailure(_, _) =>
          Future.failed(new ClientTimeoutException(s"Unable to get shard locations after ${timeout.duration.pretty}"))
        case _ => throw new IllegalArgumentException() // compiler exhaustiveness check pleaser
      }
      .map { locations =>
        new ShardLocations(locations)
      }

  override def getShardLocations(): CompletionStage[ShardLocations] = shardLocations().asJava

  override def updateShardLocations(locations: Map[ShardId, Address]): Future[Done] = {
    log.debug("updateShardLocations {} for {}", locations, Key)
    (replicator ? Update(Key, LWWMap.empty[ShardId, String], WriteLocal, None) { existing =>
      locations.foldLeft(existing) {
        case (acc, (shardId, address)) => acc.put(self, shardId, address.toString)
      }
    }).flatMap {
      case UpdateSuccess(_, _) => Future.successful(Done)
      case UpdateTimeout =>
        Future.failed(new ClientTimeoutException(s"Unable to update shard location after ${timeout.duration.pretty}"))
      case _ => throw new IllegalArgumentException() // compiler exhaustiveness check pleaser
    }
  }

  override def setShardLocations(locations: java.util.Map[ShardId, Address]): CompletionStage[Done] =
    updateShardLocations(locations.asScala.toMap).asJava
}

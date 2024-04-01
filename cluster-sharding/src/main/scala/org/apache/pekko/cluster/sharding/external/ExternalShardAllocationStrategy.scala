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

package org.apache.pekko.cluster.sharding.external

import scala.annotation.varargs
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorRef
import pekko.actor.ActorRefScope
import pekko.actor.Address
import pekko.actor.AddressFromURIString
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.NoSerializationVerificationNeeded
import pekko.actor.Props
import pekko.actor.Stash
import pekko.cluster.Cluster
import pekko.cluster.ddata.DistributedData
import pekko.cluster.ddata.LWWMapKey
import pekko.cluster.ddata.Replicator.Changed
import pekko.cluster.ddata.Replicator.Subscribe
import pekko.cluster.sharding.ShardCoordinator
import pekko.cluster.sharding.ShardRegion.ShardId
import pekko.event.Logging
import pekko.pattern.AskTimeoutException
import pekko.util.Timeout
import pekko.util.JavaDurationConverters._

object ExternalShardAllocationStrategy {

  type ShardRegion = ActorRef

  /**
   * Scala API: Create an [[ExternalShardAllocationStrategy]]
   */
  def apply(systemProvider: ClassicActorSystemProvider, typeName: String)(
      implicit timeout: Timeout = 5.seconds): ExternalShardAllocationStrategy =
    new ExternalShardAllocationStrategy(systemProvider, typeName)(timeout)

  /**
   * Java API: Create an [[ExternalShardAllocationStrategy]]
   */
  def create(systemProvider: ClassicActorSystemProvider, typeName: String, duration: java.time.Duration)
      : ExternalShardAllocationStrategy =
    this.apply(systemProvider, typeName)(duration.asScala)

  // local only messages
  private[pekko] final case class GetShardLocation(shard: ShardId)
  private[pekko] case object GetShardLocations
  private[pekko] final case class GetShardLocationsResponse(desiredAllocations: Map[ShardId, Address])
  private[pekko] final case class GetShardLocationResponse(address: Option[Address])

  // only returned locally, serialized as a string
  final case class ShardLocation(address: Address) extends NoSerializationVerificationNeeded

  private object DDataStateActor {
    def props(typeName: String) = Props(new DDataStateActor(typeName))
  }

  // uses a string primitive types are optimized in ddata to not serialize every entity
  // separately
  private[pekko] def ddataKey(typeName: String): LWWMapKey[ShardId, String] = {
    LWWMapKey[ShardId, String](s"external-sharding-$typeName")
  }

  private class DDataStateActor(typeName: String) extends Actor with ActorLogging with Stash {

    private val replicator = DistributedData(context.system).replicator

    private val Key = ddataKey(typeName)

    override def preStart(): Unit = {
      log.debug("Starting ddata state actor for [{}]", typeName)
      replicator ! Subscribe(Key, self)
    }

    var currentLocations: Map[ShardId, String] = Map.empty

    override def receive: Receive = {
      case c @ Changed(key: LWWMapKey[ShardId, String] @unchecked) =>
        val newLocations = c.get(key).entries
        currentLocations ++= newLocations
        log.debug("Received updated shard locations [{}] all locations are now [{}]", newLocations, currentLocations)
      case GetShardLocation(shard) =>
        log.debug("GetShardLocation [{}]", shard)
        val shardLocation = currentLocations.get(shard).map(asStr => AddressFromURIString(asStr))
        sender() ! GetShardLocationResponse(shardLocation)
      case GetShardLocations =>
        log.debug("GetShardLocations")
        sender() ! GetShardLocationsResponse(currentLocations.transform((_, asStr) => AddressFromURIString(asStr)))
    }

  }
}

class ExternalShardAllocationStrategy(systemProvider: ClassicActorSystemProvider, typeName: String)(
    // local only ask
    implicit val timeout: Timeout = Timeout(5.seconds))
    extends ShardCoordinator.StartableAllocationStrategy {

  private val system = systemProvider.classicSystem

  import ExternalShardAllocationStrategy._
  import system.dispatcher

  import pekko.pattern.ask

  private val log = Logging(system, classOf[ExternalShardAllocationStrategy])

  private var shardState: ActorRef = _

  private[pekko] def createShardStateActor(): ActorRef = {
    system
      .asInstanceOf[ExtendedActorSystem]
      .systemActorOf(DDataStateActor.props(typeName), s"external-allocation-state-$typeName")
  }

  private val cluster = Cluster(system)

  override def start(): Unit = {
    shardState = createShardStateActor()
  }

  override def allocateShard(
      requester: ShardRegion,
      shardId: ShardId,
      currentShardAllocations: Map[ShardRegion, immutable.IndexedSeq[ShardId]]): Future[ShardRegion] = {

    log.debug("allocateShard [{}] [{}] [{}]", shardId, requester, currentShardAllocations)

    // current shard allocations include all current shard regions
    (shardState ? GetShardLocation(shardId))
      .mapTo[GetShardLocationResponse]
      .map {
        case GetShardLocationResponse(None) =>
          log.debug("No specific location for shard [{}]. Allocating to requester [{}]", shardId, requester)
          requester
        case GetShardLocationResponse(Some(address)) =>
          // if it is the local address, convert it so it is found in the shards
          if (address == cluster.selfAddress) {
            currentShardAllocations.keys.find(_.path.address.hasLocalScope) match {
              case None =>
                log.debug("unable to find local shard in currentShardAllocation. Using requester")
                requester
              case Some(localShardRegion) =>
                log.debug("allocating to local shard")
                localShardRegion
            }
          } else {
            currentShardAllocations.keys.find(_.path.address == address) match {
              case None =>
                log.debug(
                  "External shard location [{}] for shard [{}] not found in members [{}]",
                  address,
                  shardId,
                  currentShardAllocations.keys.mkString(","))
                requester
              case Some(location) =>
                log.debug("Allocating shard to location [{}]", location)
                location
            }
          }
      }
      .recover {
        case _: AskTimeoutException =>
          log.warning(
            "allocate timed out waiting for shard allocation state [{}]. Allocating to requester [{}]",
            shardId,
            requester)
          requester
      }

  }

  override def rebalance(
      currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
      rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {

    log.debug("rebalance [{}] [{}]", currentShardAllocations, rebalanceInProgress)

    val currentAllocationByAddress: Map[Address, immutable.IndexedSeq[ShardId]] = currentShardAllocations.map {
      case (ref: ActorRefScope, value) if ref.isLocal =>
        (cluster.selfAddress, value) // so it can be compared to a address with host and port
      case (key, value) => (key.path.address, value)
    }

    val currentlyAllocatedShards: Set[ShardId] = currentShardAllocations.foldLeft(Set.empty[ShardId]) {
      case (acc, next) => acc ++ next._2.toSet
    }

    log.debug("Current allocations by address: [{}]", currentAllocationByAddress)

    val shardsThatNeedRebalanced: Future[Set[ShardId]] =
      for {
        desiredMappings <- (shardState ? GetShardLocations).mapTo[GetShardLocationsResponse]
      } yield {
        log.debug("desired allocations: [{}]", desiredMappings.desiredAllocations)
        desiredMappings.desiredAllocations.filter {
          case (shardId, expectedLocation) if currentlyAllocatedShards.contains(shardId) =>
            currentAllocationByAddress.get(expectedLocation) match {
              case None =>
                log.debug(
                  "Shard [{}] desired location [{}] is not part of the cluster, not rebalancing",
                  shardId,
                  expectedLocation)
                false // not a current allocation so don't rebalance yet
              case Some(shards) =>
                val inCorrectLocation = shards.contains(shardId)
                !inCorrectLocation
            }
          case (shardId, _) =>
            log.debug("Shard [{}] not currently allocated so not rebalancing to desired location", shardId)
            false
        }
      }.keys.toSet

    shardsThatNeedRebalanced
      .map { done =>
        if (done.nonEmpty) {
          log.debug("Shards not currently in their desired location [{}]", done)
        }
        done
      }
      .recover {
        case _: AskTimeoutException =>
          log.warning("rebalance timed out waiting for shard allocation state. Keeping existing allocations")
          Set.empty
      }
  }

}

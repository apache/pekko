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

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.actor.Address
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.cluster.sbr.DowningStrategy
import pekko.event.LogMarker

/**
 * This is public with the purpose to document the used markers and properties of log events.
 * No guarantee that it will remain binary compatible, but the marker names and properties
 * are considered public API and will not be changed without notice.
 */
@ApiMayChange
object ClusterLogMarker {

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] object Properties {
    val MemberStatus = "pekkoMemberStatus"
    val SbrDecision = "pekkoSbrDecision"
  }

  /**
   * Marker "pekkoUnreachable" of log event when a node is marked as unreachable based no failure detector observation.
   * @param node The address of the node that is marked as unreachable. Included as property "pekkoRemoteAddress".
   */
  def unreachable(node: Address): LogMarker =
    LogMarker("pekkoUnreachable", Map(LogMarker.Properties.RemoteAddress -> node))

  /**
   * Marker "pekkoReachable" of log event when a node is marked as reachable again based no failure detector observation.
   * @param node The address of the node that is marked as reachable. Included as property "pekkoRemoteAddress".
   */
  def reachable(node: Address): LogMarker =
    LogMarker("pekkoReachable", Map(LogMarker.Properties.RemoteAddress -> node))

  /**
   * Marker "pekkoHeartbeatStarvation" of log event when scheduled heartbeat was delayed.
   */
  val heartbeatStarvation: LogMarker =
    LogMarker("pekkoHeartbeatStarvation")

  /**
   * Marker "pekkoClusterLeaderIncapacitated" of log event when leader can't perform its duties.
   * Typically because there are unreachable nodes that have not been downed.
   */
  val leaderIncapacitated: LogMarker =
    LogMarker("pekkoClusterLeaderIncapacitated")

  /**
   * Marker "pekkoClusterLeaderRestored" of log event when leader can perform its duties again.
   */
  val leaderRestored: LogMarker =
    LogMarker("pekkoClusterLeaderRestored")

  /**
   * Marker "pekkoJoinFailed" of log event when node couldn't join seed nodes.
   */
  val joinFailed: LogMarker =
    LogMarker("pekkoJoinFailed")

  /**
   * Marker "pekkoMemberChanged" of log event when a member's status is changed by the leader.
   * @param node The address of the node that is changed. Included as property "pekkoRemoteAddress"
   *             and "pekkoRemoteAddressUid".
   * @param status New member status. Included as property "pekkoMemberStatus".
   */
  def memberChanged(node: UniqueAddress, status: MemberStatus): LogMarker =
    LogMarker(
      "pekkoMemberChanged",
      Map(
        LogMarker.Properties.RemoteAddress -> node.address,
        LogMarker.Properties.RemoteAddressUid -> node.longUid,
        Properties.MemberStatus -> status))

  /**
   * Marker "pekkoClusterSingletonStarted" of log event when Cluster Singleton
   * instance has started.
   */
  val singletonStarted: LogMarker =
    LogMarker("pekkoClusterSingletonStarted")

  /**
   * Marker "pekkoClusterSingletonTerminated" of log event when Cluster Singleton
   * instance has terminated.
   */
  val singletonTerminated: LogMarker =
    LogMarker("pekkoClusterSingletonTerminated")

  /**
   * Marker "pekkoSbrDowning" of log event when Split Brain Resolver has made a downing decision. Followed
   * by [[ClusterLogMarker.sbrDowningNode]] for each node that is downed.
   * @param decision The downing decision. Included as property "pekkoSbrDecision".
   */
  def sbrDowning(decision: DowningStrategy.Decision): LogMarker =
    LogMarker("pekkoSbrDowning", Map(Properties.SbrDecision -> decision))

  /**
   * Marker "pekkoSbrDowningNode" of log event when a member is downed by Split Brain Resolver.
   * @param node The address of the node that is downed. Included as property "pekkoRemoteAddress"
   *             and "pekkoRemoteAddressUid".
   * @param decision The downing decision. Included as property "pekkoSbrDecision".
   */
  def sbrDowningNode(node: UniqueAddress, decision: DowningStrategy.Decision): LogMarker =
    LogMarker(
      "pekkoSbrDowningNode",
      Map(
        LogMarker.Properties.RemoteAddress -> node.address,
        LogMarker.Properties.RemoteAddressUid -> node.longUid,
        Properties.SbrDecision -> decision))

  /**
   * Marker "pekkoSbrInstability" of log event when Split Brain Resolver has detected too much instability
   * and will down all nodes.
   */
  val sbrInstability: LogMarker =
    LogMarker("pekkoSbrInstability")

  /**
   * Marker "pekkoSbrLeaseAcquired" of log event when Split Brain Resolver has acquired the lease.
   * @param decision The downing decision. Included as property "pekkoSbrDecision".
   */
  def sbrLeaseAcquired(decision: DowningStrategy.Decision): LogMarker =
    LogMarker("pekkoSbrLeaseAcquired", Map(Properties.SbrDecision -> decision))

  /**
   * Marker "pekkoSbrLeaseDenied" of log event when Split Brain Resolver has acquired the lease.
   * @param reverseDecision The (reverse) downing decision. Included as property "pekkoSbrDecision".
   */
  def sbrLeaseDenied(reverseDecision: DowningStrategy.Decision): LogMarker =
    LogMarker("pekkoSbrLeaseDenied", Map(Properties.SbrDecision -> reverseDecision))

  /**
   * Marker "pekkoSbrLeaseReleased" of log event when Split Brain Resolver has released the lease.
   */
  val sbrLeaseReleased: LogMarker =
    LogMarker("pekkoSbrLeaseReleased")

}

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

package org.apache.pekko.cluster.sbr

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.Props
import pekko.cluster.Cluster
import pekko.cluster.DowningProvider
import pekko.coordination.lease.scaladsl.LeaseProvider

/**
 * See reference documentation: https://pekko.apache.org/docs/pekko/current/split-brain-resolver.html
 *
 * Enabled with configuration:
 * {{{
 * pekko.cluster.downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
 * }}}
 */
final class SplitBrainResolverProvider(system: ActorSystem) extends DowningProvider {

  private val settings = new SplitBrainResolverSettings(system.settings.config)

  override def downRemovalMargin: FiniteDuration = {
    // if down-removal-margin is defined we let it trump stable-after to allow
    // for two different values for SBR downing and cluster tool stop/start after downing
    val drm = Cluster(system).settings.DownRemovalMargin
    if (drm != Duration.Zero) drm
    else settings.DowningStableAfter
  }

  override def downingActorProps: Option[Props] = {
    import SplitBrainResolverSettings._

    val cluster = Cluster(system)
    val selfDc = cluster.selfDataCenter
    val strategy =
      settings.DowningStrategy match {
        case KeepMajorityName =>
          new KeepMajority(selfDc, settings.keepMajorityRole, cluster.selfUniqueAddress)
        case StaticQuorumName =>
          val s = settings.staticQuorumSettings
          new StaticQuorum(selfDc, s.size, s.role, cluster.selfUniqueAddress)
        case KeepOldestName =>
          val s = settings.keepOldestSettings
          new KeepOldest(selfDc, s.downIfAlone, s.role, cluster.selfUniqueAddress)
        case DownAllName =>
          new DownAllNodes(selfDc, cluster.selfUniqueAddress)
        case LeaseMajorityName =>
          val s = settings.leaseMajoritySettings
          val leaseOwnerName = cluster.selfUniqueAddress.address.hostPort
          val leaseName = s.safeLeaseName(system.name)
          val lease = LeaseProvider(system).getLease(leaseName, s.leaseImplementation, leaseOwnerName)
          new LeaseMajority(
            selfDc,
            s.role,
            lease,
            s.acquireLeaseDelayForMinority,
            s.releaseAfter,
            cluster.selfUniqueAddress)
      }

    Some(SplitBrainResolver.props(settings.DowningStableAfter, strategy))
  }

}

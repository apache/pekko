/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.actor.Address
import pekko.util.Version

object TestMember {
  def apply(address: Address, status: MemberStatus): Member =
    apply(address, status, Set.empty[String])

  def apply(address: Address, status: MemberStatus, upNumber: Int, dc: ClusterSettings.DataCenter): Member =
    apply(address, status, Set.empty, dc, upNumber)

  def apply(
      address: Address,
      status: MemberStatus,
      roles: Set[String],
      dataCenter: ClusterSettings.DataCenter = ClusterSettings.DefaultDataCenter,
      upNumber: Int = Int.MaxValue,
      appVersion: Version = Version.Zero): Member =
    withUniqueAddress(UniqueAddress(address, 0L), status, roles, dataCenter, upNumber, appVersion)

  def withUniqueAddress(
      uniqueAddress: UniqueAddress,
      status: MemberStatus,
      roles: Set[String],
      dataCenter: ClusterSettings.DataCenter,
      upNumber: Int = Int.MaxValue,
      appVersion: Version = Version.Zero): Member =
    new Member(uniqueAddress, upNumber, status, roles + (ClusterSettings.DcRolePrefix + dataCenter), appVersion)
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sbr

import org.apache.pekko
import pekko.actor.Address
import pekko.cluster.ClusterSettings
import pekko.cluster.Member
import pekko.cluster.MemberStatus
import pekko.cluster.MemberStatus.Up
import pekko.cluster.MemberStatus.WeaklyUp
import pekko.cluster.UniqueAddress
import pekko.util.Version

/**
 * Needed since the Member constructor is pekko private
 */
object TestAddresses {
  private def dcRole(dc: ClusterSettings.DataCenter): String =
    ClusterSettings.DcRolePrefix + dc
  val defaultDataCenter = ClusterSettings.DefaultDataCenter
  private def defaultDcRole = dcRole(defaultDataCenter)

  val addressA = Address("pekko.tcp", "sys", "a", 7355)
  val memberA = new Member(UniqueAddress(addressA, 0L), 5, Up, Set("role3", defaultDcRole), Version.Zero)
  val memberB =
    new Member(
      UniqueAddress(addressA.copy(host = Some("b")), 0L),
      4,
      Up,
      Set("role1", "role3", defaultDcRole),
      Version.Zero)
  val memberC =
    new Member(UniqueAddress(addressA.copy(host = Some("c")), 0L), 3, Up, Set("role2", defaultDcRole), Version.Zero)
  val memberD =
    new Member(
      UniqueAddress(addressA.copy(host = Some("d")), 0L),
      2,
      Up,
      Set("role1", "role2", "role3", defaultDcRole),
      Version.Zero)
  val memberE =
    new Member(UniqueAddress(addressA.copy(host = Some("e")), 0L), 1, Up, Set(defaultDcRole), Version.Zero)
  val memberF =
    new Member(UniqueAddress(addressA.copy(host = Some("f")), 0L), 5, Up, Set(defaultDcRole), Version.Zero)
  val memberG =
    new Member(UniqueAddress(addressA.copy(host = Some("g")), 0L), 6, Up, Set(defaultDcRole), Version.Zero)

  val memberAWeaklyUp = new Member(memberA.uniqueAddress, Int.MaxValue, WeaklyUp, memberA.roles, Version.Zero)
  val memberBWeaklyUp = new Member(memberB.uniqueAddress, Int.MaxValue, WeaklyUp, memberB.roles, Version.Zero)

  def dcMember(dc: ClusterSettings.DataCenter, m: Member): Member =
    new Member(
      m.uniqueAddress,
      m.upNumber,
      m.status,
      m.roles.filterNot(_.startsWith(ClusterSettings.DcRolePrefix)) + dcRole(dc),
      Version.Zero)

  def dataCenter(dc: ClusterSettings.DataCenter, members: Member*): Set[Member] =
    members.toSet[Member].map(m => dcMember(dc, m))

  def joining(m: Member): Member = Member(m.uniqueAddress, m.roles, Version.Zero)

  def leaving(m: Member): Member = m.copy(MemberStatus.Leaving)

  def exiting(m: Member): Member = leaving(m).copy(MemberStatus.Exiting)

  def downed(m: Member): Member = m.copy(MemberStatus.Down)
}

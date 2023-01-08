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

package org.apache.pekko.cluster.testkit

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.Address
import pekko.actor.Props
import pekko.actor.Scheduler
import pekko.cluster.ClusterEvent._
import pekko.cluster.Member
import pekko.cluster.MemberStatus._
import pekko.cluster.TestMember
import pekko.remote.RARP
import pekko.testkit.PekkoSpec
import pekko.testkit.TimingTest

object AutoDownSpec {
  final case class DownCalled(address: Address)

  class AutoDownTestActor(memberA: Member, autoDownUnreachableAfter: FiniteDuration, probe: ActorRef)
      extends AutoDownBase(autoDownUnreachableAfter) {

    override def selfAddress = memberA.address
    override def scheduler: Scheduler = context.system.scheduler

    override def down(node: Address): Unit = {
      if (leader)
        probe ! DownCalled(node)
      else
        probe ! "down must only be done by leader"
    }

  }

}

class AutoDownSpec extends PekkoSpec("""
    |pekko.actor.provider=remote
    |pekko.remote.warn-about-direct-use=off
    |""".stripMargin) {
  import AutoDownSpec._

  val protocol =
    if (RARP(system).provider.remoteSettings.Artery.Enabled) "akka"
    else "akka.tcp"

  val memberA = TestMember(Address(protocol, "sys", "a", 2552), Up)
  val memberB = TestMember(Address(protocol, "sys", "b", 2552), Up)
  val memberC = TestMember(Address(protocol, "sys", "c", 2552), Up)

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(classOf[AutoDownTestActor], memberA, autoDownUnreachableAfter, testActor))

  "AutoDown" must {

    "down unreachable when leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectMsg(DownCalled(memberB.address))
    }

    "not down unreachable when not leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberB.address))
      a ! UnreachableMember(memberC)
      expectNoMessage(1.second)
    }

    "down unreachable when becoming leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! LeaderChanged(Some(memberA.address))
      expectMsg(DownCalled(memberC.address))
    }

    "down unreachable after specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberB.address))
    }

    "down unreachable when becoming leader in-between detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! LeaderChanged(Some(memberA.address))
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberC.address))
    }

    "not down unreachable when losing leadership in-between detection and specified duration" taggedAs TimingTest in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberC)
      a ! LeaderChanged(Some(memberB.address))
      expectNoMessage(3.second)
    }

    "not down when unreachable become reachable in-between detection and specified duration" taggedAs TimingTest in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMessage(3.second)
    }

    "not down when unreachable is removed in-between detection and specified duration" taggedAs TimingTest in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMessage(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMessage(1.second)
    }

  }
}

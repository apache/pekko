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

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.Done
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.cluster.ClusterEvent._
import pekko.cluster.MemberStatus._

/**
 * INTERNAL API
 */
private[pekko] object CoordinatedShutdownLeave {
  def props(): Props = Props[CoordinatedShutdownLeave]()

  case object LeaveReq
}

/**
 * INTERNAL API
 */
private[pekko] class CoordinatedShutdownLeave extends Actor {
  import CoordinatedShutdownLeave.LeaveReq

  val cluster = Cluster(context.system)

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case LeaveReq =>
      // MemberRemoved is needed in case it was downed instead
      cluster.leave(cluster.selfAddress)
      cluster.subscribe(self, classOf[MemberLeft], classOf[MemberRemoved])
      context.become(waitingLeaveCompleted(sender()))
  }

  def waitingLeaveCompleted(replyTo: ActorRef): Receive = {
    case s: CurrentClusterState =>
      if (s.members.isEmpty) {
        // not joined yet
        done(replyTo)
      } else if (s.members.exists(m =>
          m.uniqueAddress == cluster.selfUniqueAddress &&
          (m.status == Leaving || m.status == Exiting || m.status == Down))) {
        done(replyTo)
      }
    case MemberLeft(m) =>
      if (m.uniqueAddress == cluster.selfUniqueAddress)
        done(replyTo)
    case MemberDowned(m) =>
      // in case it was downed instead
      if (m.uniqueAddress == cluster.selfUniqueAddress)
        done(replyTo)
    case MemberRemoved(m, _) =>
      // final safety fallback
      if (m.uniqueAddress == cluster.selfUniqueAddress)
        done(replyTo)
  }

  private def done(replyTo: ActorRef): Unit = {
    replyTo ! Done
    context.stop(self)
  }

}

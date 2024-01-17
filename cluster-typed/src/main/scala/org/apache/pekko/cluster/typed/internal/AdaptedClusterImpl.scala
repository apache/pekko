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

package org.apache.pekko.cluster.typed.internal

import org.apache.pekko
import pekko.actor.typed.{ ActorRef, ActorSystem, Terminated }
import pekko.actor.typed.Behavior
import pekko.actor.typed.Props
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.internal.adapter.ActorSystemAdapter
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.annotation.InternalApi
import pekko.cluster.{ ClusterEvent, Member, MemberStatus }
import pekko.cluster.ClusterEvent.MemberEvent
import pekko.cluster.typed.PrepareForFullClusterShutdown
import pekko.cluster.typed._

/**
 * INTERNAL API:
 */
@InternalApi
private[pekko] object AdapterClusterImpl {

  private sealed trait SeenState
  private case object BeforeUp extends SeenState
  private case object Up extends SeenState
  private case class Removed(previousStatus: MemberStatus) extends SeenState

  private def subscriptionsBehavior(adaptedCluster: pekko.cluster.Cluster): Behavior[ClusterStateSubscription] =
    Behaviors.setup[ClusterStateSubscription] { ctx =>
      var seenState: SeenState = BeforeUp
      var upSubscribers: List[ActorRef[SelfUp]] = Nil
      var removedSubscribers: List[ActorRef[SelfRemoved]] = Nil

      adaptedCluster.subscribe(ctx.self.toClassic, ClusterEvent.initialStateAsEvents, classOf[MemberEvent])

      // important to not eagerly refer to it or we get a cycle here
      lazy val cluster = Cluster(ctx.system)

      def onSelfMemberEvent(event: MemberEvent): Unit = {
        event match {
          case ClusterEvent.MemberUp(_) =>
            seenState = Up
            val upMessage = SelfUp(cluster.state)
            upSubscribers.foreach(_ ! upMessage)
            upSubscribers = Nil

          case ClusterEvent.MemberRemoved(_, previousStatus) =>
            seenState = Removed(previousStatus)
            val removedMessage = SelfRemoved(previousStatus)
            removedSubscribers.foreach(_ ! removedMessage)
            removedSubscribers = Nil

          case _ => // This is fine.
        }
      }

      Behaviors
        .receiveMessage[AnyRef] {
          case Subscribe(subscriber: ActorRef[SelfUp] @unchecked, clazz) if clazz == classOf[SelfUp] =>
            seenState match {
              case Up => subscriber ! SelfUp(adaptedCluster.state)
              case BeforeUp =>
                ctx.watch(subscriber)
                upSubscribers = subscriber :: upSubscribers
              case _: Removed =>
              // self did join, but is now no longer up, we want to avoid subscribing
              // to not get a memory leak, but also not signal anything
            }
            Behaviors.same

          case Subscribe(subscriber: ActorRef[SelfRemoved] @unchecked, clazz) if clazz == classOf[SelfRemoved] =>
            seenState match {
              case BeforeUp | Up => removedSubscribers = subscriber :: removedSubscribers
              case Removed(s)    => subscriber ! SelfRemoved(s)
            }
            Behaviors.same

          case Subscribe(subscriber, eventClass) =>
            adaptedCluster.subscribe(
              subscriber.toClassic,
              initialStateMode = ClusterEvent.initialStateAsEvents,
              eventClass)
            Behaviors.same

          case Unsubscribe(subscriber) =>
            adaptedCluster.unsubscribe(subscriber.toClassic)
            Behaviors.same

          case GetCurrentState(sender) =>
            adaptedCluster.sendCurrentClusterState(sender.toClassic)
            Behaviors.same

          case evt: MemberEvent if evt.member.uniqueAddress == cluster.selfMember.uniqueAddress =>
            onSelfMemberEvent(evt)
            Behaviors.same

          case _: MemberEvent =>
            Behaviors.same

          case _ => throw new IllegalArgumentException() // compiler exhaustiveness check pleaser

        }
        .receiveSignal {

          case (_, Terminated(ref)) =>
            upSubscribers = upSubscribers.filterNot(_ == ref)
            removedSubscribers = removedSubscribers.filterNot(_ == ref)
            Behaviors.same

        }
        .narrow[ClusterStateSubscription]
    }

  private def managerBehavior(adaptedCluster: pekko.cluster.Cluster): Behavior[ClusterCommand] = {
    Behaviors.receiveMessage {
      case Join(address) =>
        adaptedCluster.join(address)
        Behaviors.same

      case Leave(address) =>
        adaptedCluster.leave(address)
        Behaviors.same

      case Down(address) =>
        adaptedCluster.down(address)
        Behaviors.same

      case JoinSeedNodes(addresses) =>
        adaptedCluster.joinSeedNodes(addresses)
        Behaviors.same

      case PrepareForFullClusterShutdown =>
        adaptedCluster.prepareForFullClusterShutdown()
        Behaviors.same

    }
  }

}

/**
 * INTERNAL API:
 */
@InternalApi
private[pekko] final class AdapterClusterImpl(system: ActorSystem[?]) extends Cluster {
  import AdapterClusterImpl._

  require(system.isInstanceOf[ActorSystemAdapter[?]], "only adapted actor systems can be used for cluster features")
  private val classicCluster = pekko.cluster.Cluster(system)

  override def selfMember: Member = classicCluster.selfMember
  override def isTerminated: Boolean = classicCluster.isTerminated
  override def state: ClusterEvent.CurrentClusterState = classicCluster.state

  // must not be lazy as it also updates the cached selfMember
  override val subscriptions: ActorRef[ClusterStateSubscription] =
    system.internalSystemActorOf(
      // resume supervision: has state that shouldn't be lost in case of failure
      Behaviors.supervise(subscriptionsBehavior(classicCluster)).onFailure(SupervisorStrategy.resume),
      "clusterStateSubscriptions",
      Props.empty)

  override lazy val manager: ActorRef[ClusterCommand] =
    system.internalSystemActorOf(
      // restart supervision: no state lost in case of failure
      Behaviors.supervise(managerBehavior(classicCluster)).onFailure(SupervisorStrategy.restart),
      "clusterCommandManager",
      Props.empty)

}

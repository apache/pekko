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

package org.apache.pekko.cluster.sharding.typed.scaladsl

import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.annotation.DoNotInherit
import pekko.annotation.InternalApi
import pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import pekko.cluster.sharding.typed.ShardedDaemonProcessSettings
import pekko.cluster.sharding.typed.internal.ShardedDaemonProcessImpl
import pekko.cluster.sharding.typed.javadsl

object ShardedDaemonProcess extends ExtensionId[ShardedDaemonProcess] {
  override def createExtension(system: ActorSystem[_]): ShardedDaemonProcess = new ShardedDaemonProcessImpl(system)
}

/**
 * This extension runs a pre set number of actors in a cluster.
 *
 * The typical use case is when you have a task that can be divided in a number of workers, each doing a
 * sharded part of the work, for example consuming the read side events from Pekko Persistence through
 * tagged events where each tag decides which consumer that should consume the event.
 *
 * Each named set needs to be started on all the nodes of the cluster on start up.
 *
 * The processes are spread out across the cluster, when the cluster topology changes the processes may be stopped
 * and started anew on a new node to rebalance them.
 *
 * Not for user extension.
 */
@DoNotInherit
trait ShardedDaemonProcess extends Extension { javadslSelf: javadsl.ShardedDaemonProcess =>

  /**
   * Start a specific number of actors that is then kept alive in the cluster.
   * @param behaviorFactory Given a unique id of `0` until `numberOfInstance` create the behavior for that actor.
   */
  def init[T](name: String, numberOfInstances: Int, behaviorFactory: Int => Behavior[T])(
      implicit classTag: ClassTag[T]): Unit

  /**
   * Start a specific number of actors that is then kept alive in the cluster.
   *
   * @param behaviorFactory Given a unique id of `0` until `numberOfInstance` create the behavior for that actor.
   * @param stopMessage sent to the actors when they need to stop because of a rebalance across the nodes of the cluster
   *                    or cluster shutdown.
   */
  def init[T](name: String, numberOfInstances: Int, behaviorFactory: Int => Behavior[T], stopMessage: T)(
      implicit classTag: ClassTag[T]): Unit

  /**
   * Start a specific number of actors, each with a unique numeric id in the set, that is then kept alive in the cluster.
   * @param behaviorFactory Given a unique id of `0` until `numberOfInstance` create the behavior for that actor.
   * @param stopMessage if defined sent to the actors when they need to stop because of a rebalance across the nodes of the cluster
   *                    or cluster shutdown.
   */
  def init[T](
      name: String,
      numberOfInstances: Int,
      behaviorFactory: Int => Behavior[T],
      settings: ShardedDaemonProcessSettings,
      stopMessage: Option[T])(implicit classTag: ClassTag[T]): Unit

  /**
   * Start a specific number of actors, each with a unique numeric id in the set, that is then kept alive in the cluster.
   * @param behaviorFactory Given a unique id of `0` until `numberOfInstance` create the behavior for that actor.
   * @param stopMessage if defined sent to the actors when they need to stop because of a rebalance across the nodes of the cluster
   *                    or cluster shutdown.
   * @param shardAllocationStrategy if defined used by entities to control the shard allocation
   */
  def init[T](
      name: String,
      numberOfInstances: Int,
      behaviorFactory: Int => Behavior[T],
      settings: ShardedDaemonProcessSettings,
      stopMessage: Option[T],
      shardAllocationStrategy: Option[ShardAllocationStrategy])(implicit classTag: ClassTag[T]): Unit

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def asJava: javadsl.ShardedDaemonProcess = javadslSelf

}

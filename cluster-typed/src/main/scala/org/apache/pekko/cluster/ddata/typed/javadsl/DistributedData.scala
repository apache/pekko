/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata.typed.javadsl

import java.util.function.{ Function => JFunction }

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.actor.typed.ExtensionSetup
import pekko.actor.typed.javadsl.Behaviors
import pekko.annotation.DoNotInherit
import pekko.annotation.InternalApi
import pekko.cluster.ddata.ReplicatedData
import pekko.cluster.ddata.SelfUniqueAddress
import pekko.util.JavaDurationConverters._

object DistributedData extends ExtensionId[DistributedData] {
  def get(system: ActorSystem[_]): DistributedData = apply(system)

  override def createExtension(system: ActorSystem[_]): DistributedData =
    new DistributedDataImpl(system)

  /**
   * When interacting with the [[DistributedData.replicator]] from an actor the [[ReplicatorMessageAdapter]]
   * provides convenient methods that adapts the response messages to the requesting actor's message protocol.
   *
   * One `ReplicatorMessageAdapter` instance can be used for a given `ReplicatedData` type,
   * e.g. an `OrSet<String>`. Interaction with several [[pekko.cluster.ddata.Key]]s can be used via the same adapter
   * but they must all be of the same `ReplicatedData` type. For interaction with several different
   * `ReplicatedData` types, e.g. an `OrSet<String>` and a `GCounter`, an adapter can be created
   * for each type.
   *
   * *Warning*: A `ReplicatorMessageAdapter` instance is not thread-safe and must only be used from a single actor
   * It must not be accessed from threads other than the ordinary actor message processing thread, such as
   * [[java.util.concurrent.CompletionStage]] callbacks. It must not be shared between several actor instances.
   *
   * @param factory Factory of the `Behavior` for the actor that is using the `ReplicatorMessageAdapter`
   *
   * @tparam A Message type of the requesting actor.
   * @tparam B Type of the [[ReplicatedData]].
   */
  def withReplicatorMessageAdapter[A, B <: ReplicatedData](
      factory: JFunction[ReplicatorMessageAdapter[A, B], Behavior[A]]): Behavior[A] = {
    Behaviors.setup[A] { context =>
      val distributedData = pekko.cluster.ddata.typed.scaladsl.DistributedData(context.getSystem)
      val replicatorAdapter =
        new ReplicatorMessageAdapter[A, B](
          context,
          distributedData.replicator,
          distributedData.unexpectedAskTimeout.asJava)
      factory(replicatorAdapter)
    }
  }
}

/**
 * Akka extension for convenient configuration and use of the
 * [[Replicator]]. Configuration settings are defined in the
 * `pekko.cluster.ddata` section, see `reference.conf`.
 *
 * This is using the same underlying `Replicator` instance as
 * [[pekko.cluster.ddata.DistributedData]] and that means that typed
 * and classic actors can share the same data.
 *
 * This class is not intended for user extension other than for test purposes (e.g.
 * stub implementation). More methods may be added in the future and that may break
 * such implementations.
 */
@DoNotInherit
abstract class DistributedData extends Extension {

  /**
   * `ActorRef` of the [[Replicator]].
   *
   * @see [[DistributedData.withReplicatorMessageAdapter]]
   */
  def replicator: ActorRef[Replicator.Command]

  def selfUniqueAddress: SelfUniqueAddress
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class DistributedDataImpl(system: ActorSystem[_]) extends DistributedData {

  override val replicator: ActorRef[Replicator.Command] =
    pekko.cluster.ddata.typed.scaladsl.DistributedData(system).replicator.narrow[Replicator.Command]

  override val selfUniqueAddress: SelfUniqueAddress =
    pekko.cluster.ddata.typed.scaladsl.DistributedData(system).selfUniqueAddress

}

object DistributedDataSetup {
  def apply[T <: Extension](createExtension: ActorSystem[_] => DistributedData): DistributedDataSetup =
    new DistributedDataSetup(createExtension(_))

}

/**
 * Can be used in [[pekko.actor.setup.ActorSystemSetup]] when starting the [[ActorSystem]]
 * to replace the default implementation of the [[DistributedData]] extension. Intended
 * for tests that need to replace extension with stub/mock implementations.
 */
final class DistributedDataSetup(createExtension: java.util.function.Function[ActorSystem[_], DistributedData])
    extends ExtensionSetup[DistributedData](DistributedData, createExtension)

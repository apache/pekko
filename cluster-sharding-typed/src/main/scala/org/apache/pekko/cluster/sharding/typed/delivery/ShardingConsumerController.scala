/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.delivery

import java.util.function.{ Function => JFunction }

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.delivery.ConsumerController
import pekko.actor.typed.scaladsl.Behaviors
import pekko.annotation.ApiMayChange
import pekko.cluster.sharding.typed.delivery.internal.ShardingConsumerControllerImpl

/**
 * `ShardingConsumerController` is used together with [[ShardingProducerController]]. See the description
 * in that class or the Pekko reference documentation for how they are intended to be used.
 *
 * `ShardingConsumerController` is the entity that is initialized in `ClusterSharding`. It will manage
 * the lifecycle and message delivery to the destination consumer actor.
 *
 * The destination consumer actor will start the flow by sending an initial [[pekko.actor.typed.delivery.ConsumerController.Start]]
 * message via the `ActorRef` provided in the factory function of the consumer `Behavior`.
 * The `ActorRef` in the `Start` message is typically constructed as a message adapter to map the
 * [[pekko.actor.typed.delivery.ConsumerController.Delivery]] to the protocol of the consumer actor.
 *
 * Received messages from the producer are wrapped in [[pekko.actor.typed.delivery.ConsumerController.Delivery]] when sent to the consumer,
 * which is supposed to reply with [[pekko.actor.typed.delivery.ConsumerController.Confirmed]] when it has processed the message.
 * Next message from a specific producer is not delivered until the previous is confirmed. However, since
 * there can be several producers, e.g. one per node, sending to the same destination entity there can be
 * several `Delivery` in flight at the same time.
 * More messages from a specific producer that arrive while waiting for the confirmation are stashed by
 * the `ConsumerController` and delivered when previous message was confirmed.
 */
@ApiMayChange
object ShardingConsumerController {

  object Settings {

    /**
     * Scala API: Factory method from config `pekko.reliable-delivery.sharding.consumer-controller`
     * of the `ActorSystem`.
     */
    def apply(system: ActorSystem[_]): Settings =
      apply(system.settings.config.getConfig("pekko.reliable-delivery.sharding.consumer-controller"))

    /**
     * Scala API: Factory method from Config corresponding to
     * `pekko.reliable-delivery.sharding.consumer-controller`.
     */
    def apply(config: Config): Settings =
      new Settings(bufferSize = config.getInt("buffer-size"), ConsumerController.Settings(config))

    /**
     * Java API: Factory method from config `pekko.reliable-delivery.sharding.consumer-controller`
     * of the `ActorSystem`.
     */
    def create(system: ActorSystem[_]): Settings =
      apply(system)

    /**
     * Java API: Factory method from Config corresponding to
     * `pekko.reliable-delivery.sharding.consumer-controller`.
     */
    def create(config: Config): Settings =
      apply(config)
  }

  final class Settings private (val bufferSize: Int, val consumerControllerSettings: ConsumerController.Settings) {

    def withBufferSize(newBufferSize: Int): Settings =
      copy(bufferSize = newBufferSize)

    def withConsumerControllerSettings(newConsumerControllerSettings: ConsumerController.Settings): Settings =
      copy(consumerControllerSettings = newConsumerControllerSettings)

    /**
     * Private copy method for internal use only.
     */
    private def copy(
        bufferSize: Int = bufferSize,
        consumerControllerSettings: ConsumerController.Settings = consumerControllerSettings) =
      new Settings(bufferSize, consumerControllerSettings)

    override def toString: String =
      s"Settings($bufferSize,$consumerControllerSettings)"
  }

  /**
   * The `Behavior` of the entity that is to be initialized in `ClusterSharding`. It will manage
   * the lifecycle and message delivery to the destination consumer actor.
   */
  def apply[A, B](consumerBehavior: ActorRef[ConsumerController.Start[A]] => Behavior[B])
      : Behavior[ConsumerController.SequencedMessage[A]] =
    Behaviors.setup { context =>
      ShardingConsumerControllerImpl(consumerBehavior, Settings(context.system))
    }

  /**
   * The `Behavior` of the entity that is to be initialized in `ClusterSharding`. It will manage
   * the lifecycle and message delivery to the destination consumer actor.
   */
  def withSettings[A, B](settings: Settings)(consumerBehavior: ActorRef[ConsumerController.Start[A]] => Behavior[B])
      : Behavior[ConsumerController.SequencedMessage[A]] =
    // can't overload apply, loosing type inference
    ShardingConsumerControllerImpl(consumerBehavior, settings)

  /**
   * Java API: The `Behavior` of the entity that is to be initialized in `ClusterSharding`. It will manage
   * the lifecycle and message delivery to the destination consumer actor.
   */
  def create[A, B](consumerBehavior: JFunction[ActorRef[ConsumerController.Start[A]], Behavior[B]])
      : Behavior[ConsumerController.SequencedMessage[A]] =
    apply(consumerBehavior.apply)

  /**
   * Java API: The `Behavior` of the entity that is to be initialized in `ClusterSharding`. It will manage
   * the lifecycle and message delivery to the destination consumer actor.
   */
  def create[A, B](
      consumerBehavior: JFunction[ActorRef[ConsumerController.Start[A]], Behavior[B]],
      settings: Settings): Behavior[ConsumerController.SequencedMessage[A]] =
    withSettings(settings)(consumerBehavior.apply)

  /**
   * Java API: The generic `Class` type for `ConsumerController.SequencedMessage` that can be used when creating
   * an `EntityTypeKey` for the `ShardedConsumerController` with
   * `Class<EntityTypeKey<ConsumerController.SequencedMessage<MessageType>>>`.
   */
  def entityTypeKeyClass[A]: Class[ConsumerController.SequencedMessage[A]] =
    classOf[ConsumerController.SequencedMessage[A]]

}

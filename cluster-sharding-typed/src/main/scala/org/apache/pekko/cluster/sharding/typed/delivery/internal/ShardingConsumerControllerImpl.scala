/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.delivery.internal

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.DispatcherSelector
import pekko.actor.typed.Terminated
import pekko.actor.typed.delivery.ConsumerController
import pekko.actor.typed.delivery.internal.ConsumerControllerImpl
import pekko.actor.typed.delivery.internal.ProducerControllerImpl
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.delivery.ShardingConsumerController

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ShardingConsumerControllerImpl {
  def apply[A, B](
      consumerBehavior: ActorRef[ConsumerController.Start[A]] => Behavior[B],
      settings: ShardingConsumerController.Settings): Behavior[ConsumerController.SequencedMessage[A]] = {
    Behaviors
      .setup[ConsumerController.Command[A]] { context =>
        context.setLoggerName("org.apache.pekko.cluster.sharding.typed.delivery.ShardingConsumerController")
        val consumer = context.spawn(consumerBehavior(context.self), name = "consumer")
        context.watch(consumer)
        waitForStart(context, settings, consumer)
      }
      .narrow
  }

  private def waitForStart[A](
      context: ActorContext[ConsumerController.Command[A]],
      settings: ShardingConsumerController.Settings,
      consumer: ActorRef[_]): Behavior[ConsumerController.Command[A]] = {
    Behaviors.withStash(settings.bufferSize) { stashBuffer =>
      Behaviors
        .receiveMessage[ConsumerController.Command[A]] {
          case start: ConsumerController.Start[A @unchecked] =>
            ConsumerControllerImpl.enforceLocalConsumer(start.deliverTo)
            context.unwatch(consumer)
            context.watch(start.deliverTo)
            stashBuffer.unstashAll(
              new ShardingConsumerControllerImpl[A](context, start.deliverTo, settings).active(Map.empty, Map.empty))
          case other =>
            stashBuffer.stash(other)
            Behaviors.same
        }
        .receiveSignal {
          case (_, Terminated(`consumer`)) =>
            context.log.debug("Consumer terminated before initialized.")
            Behaviors.stopped
        }
    }
  }

}

private class ShardingConsumerControllerImpl[A](
    context: ActorContext[ConsumerController.Command[A]],
    deliverTo: ActorRef[ConsumerController.Delivery[A]],
    settings: ShardingConsumerController.Settings) {

  def active(
      // ProducerController -> producerId
      producerControllers: Map[ActorRef[ProducerControllerImpl.InternalCommand], String],
      // producerId -> ConsumerController
      consumerControllers: Map[String, ActorRef[ConsumerController.Command[A]]])
      : Behavior[ConsumerController.Command[A]] = {

    Behaviors
      .receiveMessagePartial[ConsumerController.Command[A]] {
        case seqMsg: ConsumerController.SequencedMessage[A @unchecked] =>
          def updatedProducerControllers(): Map[ActorRef[ProducerControllerImpl.InternalCommand], String] = {
            producerControllers.get(seqMsg.producerController) match {
              case Some(_) =>
                producerControllers
              case None =>
                context.watch(seqMsg.producerController)
                producerControllers.updated(seqMsg.producerController, seqMsg.producerId)
            }
          }

          consumerControllers.get(seqMsg.producerId) match {
            case Some(c) =>
              c ! seqMsg
              active(updatedProducerControllers(), consumerControllers)
            case None =>
              context.log.debug("Starting ConsumerController for producerId [{}].", seqMsg.producerId)
              val cc = context.spawn(
                ConsumerController[A](settings.consumerControllerSettings),
                s"consumerController-${seqMsg.producerId}",
                DispatcherSelector.sameAsParent())
              context.watch(cc)
              cc ! ConsumerController.Start(deliverTo)
              cc ! seqMsg
              active(updatedProducerControllers(), consumerControllers.updated(seqMsg.producerId, cc))
          }
      }
      .receiveSignal {
        case (_, Terminated(`deliverTo`)) =>
          context.log.debug("Consumer terminated.")
          Behaviors.stopped
        case (_, Terminated(ref)) =>
          val producerControllerRef = ref.unsafeUpcast[ProducerControllerImpl.InternalCommand]
          producerControllers.get(producerControllerRef) match {
            case Some(producerId) =>
              context.log.debug("ProducerController for producerId [{}] terminated.", producerId)
              val newControllers = producerControllers - producerControllerRef
              consumerControllers.get(producerId).foreach { cc =>
                cc ! ConsumerController.DeliverThenStop()
              }
              active(newControllers, consumerControllers)
            case None =>
              consumerControllers.find { case (_, cc) => ref == cc } match {
                case Some((producerId, _)) =>
                  context.log.debug("ConsumerController for producerId [{}] terminated.", producerId)
                  val newControllers = consumerControllers - producerId
                  active(producerControllers, newControllers)
                case None =>
                  context.log.debug("Unknown {} terminated.", ref)
                  Behaviors.same
              }
          }
      }

  }

}

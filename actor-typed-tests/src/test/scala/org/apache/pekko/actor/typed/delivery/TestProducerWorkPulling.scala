/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.delivery

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors

object TestProducerWorkPulling {

  trait Command
  final case class RequestNext(sendTo: ActorRef[TestConsumer.Job]) extends Command
  private case object Tick extends Command

  def apply(
      delay: FiniteDuration,
      producerController: ActorRef[WorkPullingProducerController.Start[TestConsumer.Job]]): Behavior[Command] = {
    Behaviors.setup { context =>
      context.setLoggerName("TestProducerWorkPulling")
      val requestNextAdapter: ActorRef[WorkPullingProducerController.RequestNext[TestConsumer.Job]] =
        context.messageAdapter(req => RequestNext(req.sendNextTo))
      producerController ! WorkPullingProducerController.Start(requestNextAdapter)

      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Tick, Tick, delay)
        idle(0)
      }
    }
  }

  private def idle(n: Int): Behavior[Command] = {
    Behaviors.receiveMessagePartial {
      case Tick                => Behaviors.same
      case RequestNext(sendTo) => active(n + 1, sendTo)
    }
  }

  private def active(n: Int, sendTo: ActorRef[TestConsumer.Job]): Behavior[Command] = {
    Behaviors.receivePartial {
      case (ctx, Tick) =>
        val msg = s"msg-$n"
        ctx.log.info("sent {}", msg)
        sendTo ! TestConsumer.Job(msg)
        idle(n)

      case (_, RequestNext(_)) =>
        throw new IllegalStateException("Unexpected RequestNext, already got one.")
    }
  }

}

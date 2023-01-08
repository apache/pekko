/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.delivery

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors

object TestProducer {

  sealed trait Command
  final case class RequestNext(sendTo: ActorRef[TestConsumer.Job]) extends Command
  private case object Tick extends Command

  val defaultProducerDelay: FiniteDuration = 20.millis

  def apply(
      delay: FiniteDuration,
      producerController: ActorRef[ProducerController.Start[TestConsumer.Job]]): Behavior[Command] = {
    Behaviors.setup { context =>
      context.setLoggerName("TestProducer")
      val requestNextAdapter: ActorRef[ProducerController.RequestNext[TestConsumer.Job]] =
        context.messageAdapter(req => RequestNext(req.sendNextTo))
      producerController ! ProducerController.Start(requestNextAdapter)

      if (delay == Duration.Zero)
        activeNoDelay(1) // simulate fast producer
      else {
        Behaviors.withTimers { timers =>
          timers.startTimerWithFixedDelay(Tick, Tick, delay)
          idle(0)
        }
      }
    }
  }

  private def idle(n: Int): Behavior[Command] = {
    Behaviors.receiveMessage {
      case Tick                => Behaviors.same
      case RequestNext(sendTo) => active(n + 1, sendTo)
    }
  }

  private def active(n: Int, sendTo: ActorRef[TestConsumer.Job]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Tick =>
          sendMessage(n, sendTo, ctx)
          idle(n)

        case RequestNext(_) =>
          throw new IllegalStateException("Unexpected RequestNext, already got one.")
      }
    }
  }

  private def activeNoDelay(n: Int): Behavior[Command] = {
    Behaviors.receivePartial {
      case (ctx, RequestNext(sendTo)) =>
        sendMessage(n, sendTo, ctx)
        activeNoDelay(n + 1)
    }
  }

  private def sendMessage(n: Int, sendTo: ActorRef[TestConsumer.Job], ctx: ActorContext[Command]): Unit = {
    val msg = s"msg-$n"
    ctx.log.info("sent {}", msg)
    sendTo ! TestConsumer.Job(msg)
  }
}

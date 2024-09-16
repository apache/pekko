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
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.util.Timeout

object TestProducerWithAsk {

  trait Command
  final case class RequestNext(askTo: ActorRef[ProducerController.MessageWithConfirmation[TestConsumer.Job]])
      extends Command
  private case object Tick extends Command
  private final case class Confirmed(seqNr: Long) extends Command
  private case object AskTimeout extends Command

  private implicit val askTimeout: Timeout = 10.seconds

  def apply(
      delay: FiniteDuration,
      replyProbe: ActorRef[Long],
      producerController: ActorRef[ProducerController.Start[TestConsumer.Job]]): Behavior[Command] =
    Behaviors.setup { context =>
      context.setLoggerName("TestProducerWithConfirmation")
      val requestNextAdapter: ActorRef[ProducerController.RequestNext[TestConsumer.Job]] =
        context.messageAdapter(req => RequestNext(req.askNextTo))
      producerController ! ProducerController.Start(requestNextAdapter)

      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Tick, Tick, delay)
        idle(0, replyProbe)
      }
    }

  private def idle(n: Int, replyProbe: ActorRef[Long]): Behavior[Command] =
    Behaviors.receivePartial {
      case (_, Tick)                => Behaviors.same
      case (_, RequestNext(sendTo)) => active(n + 1, replyProbe, sendTo)
      case (_, Confirmed(seqNr)) =>
        replyProbe ! seqNr
        Behaviors.same
      case (ctx, AskTimeout) =>
        ctx.log.warn("Timeout")
        Behaviors.same
    }

  private def active(
      n: Int,
      replyProbe: ActorRef[Long],
      sendTo: ActorRef[ProducerController.MessageWithConfirmation[TestConsumer.Job]]): Behavior[Command] =
    Behaviors.receivePartial {
      case (ctx, Tick) =>
        val msg = s"msg-$n"
        ctx.log.info("sent {}", msg)
        ctx.ask(
          sendTo,
          (askReplyTo: ActorRef[Long]) =>
            ProducerController.MessageWithConfirmation(TestConsumer.Job(msg), askReplyTo)) {
          case Success(seqNr) => Confirmed(seqNr)
          case Failure(_)     => AskTimeout
        }
        idle(n, replyProbe)

      case (_, RequestNext(_)) =>
        throw new IllegalStateException("Unexpected RequestNext, already got one.")

      case (ctx, Confirmed(seqNr)) =>
        ctx.log.info("Reply Confirmed [{}]", seqNr)
        replyProbe ! seqNr
        Behaviors.same

      case (ctx, AskTimeout) =>
        ctx.log.warn("Timeout")
        Behaviors.same
    }

}

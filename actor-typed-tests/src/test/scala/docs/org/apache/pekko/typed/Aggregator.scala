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

package docs.org.apache.pekko.typed

//#behavior
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors

object Aggregator {

  sealed trait Command
  private case object ReceiveTimeout extends Command
  private case class WrappedReply[R](reply: R) extends Command

  def apply[Reply: ClassTag, Aggregate](
      sendRequests: ActorRef[Reply] => Unit,
      expectedReplies: Int,
      replyTo: ActorRef[Aggregate],
      aggregateReplies: immutable.IndexedSeq[Reply] => Aggregate,
      timeout: FiniteDuration): Behavior[Command] =
    Behaviors.setup { context =>
      context.setReceiveTimeout(timeout, ReceiveTimeout)
      val replyAdapter = context.messageAdapter[Reply](WrappedReply(_))
      sendRequests(replyAdapter)

      def collecting(replies: immutable.IndexedSeq[Reply]): Behavior[Command] =
        Behaviors.receiveMessage {
          case WrappedReply(reply) =>
            val newReplies = replies :+ reply.asInstanceOf[Reply]
            if (newReplies.size == expectedReplies) {
              val result = aggregateReplies(newReplies)
              replyTo ! result
              Behaviors.stopped
            } else
              collecting(newReplies)

          case ReceiveTimeout =>
            val aggregate = aggregateReplies(replies)
            replyTo ! aggregate
            Behaviors.stopped
        }

      collecting(Vector.empty)
    }

}
//#behavior

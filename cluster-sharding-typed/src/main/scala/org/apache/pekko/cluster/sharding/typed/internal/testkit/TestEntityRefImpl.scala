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

package org.apache.pekko.cluster.sharding.typed.internal.testkit

import java.time.Duration
import java.util.concurrent.CompletionStage

import scala.concurrent.Future
import org.apache.pekko
import pekko.actor.ActorRefProvider
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Scheduler
import pekko.actor.typed.internal.InternalRecipientRef
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.javadsl
import pekko.cluster.sharding.typed.javadsl.EntityRef
import pekko.cluster.sharding.typed.scaladsl
import pekko.japi.function.{ Function => JFunction }
import pekko.pattern.StatusReply
import pekko.util.FutureConverters._
import pekko.util.JavaDurationConverters._
import pekko.util.Timeout

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class TestEntityRefImpl[M](
    override val entityId: String,
    probe: ActorRef[M],
    override val typeKey: scaladsl.EntityTypeKey[M])
    extends javadsl.EntityRef[M]
    with scaladsl.EntityRef[M]
    with InternalRecipientRef[M] {

  import pekko.actor.typed.scaladsl.adapter._

  override def dataCenter: Option[String] = None

  override def tell(msg: M): Unit =
    probe ! msg

  override def ask[U](message: ActorRef[U] => M)(implicit timeout: Timeout): Future[U] = {
    import pekko.actor.typed.scaladsl.AskPattern._
    implicit val scheduler: Scheduler = provider.guardian.underlying.system.scheduler.toTyped
    probe.ask(message)
  }

  def ask[U](message: JFunction[ActorRef[U], M], timeout: Duration): CompletionStage[U] =
    ask[U](replyTo => message.apply(replyTo))(timeout.asScala).asJava

  override def askWithStatus[Res](f: ActorRef[StatusReply[Res]] => M, timeout: Duration): CompletionStage[Res] =
    askWithStatus(f)(timeout.asScala).asJava

  override def askWithStatus[Res](f: ActorRef[StatusReply[Res]] => M)(implicit timeout: Timeout): Future[Res] =
    StatusReply.flattenStatusFuture(ask(f))

  // impl InternalRecipientRef
  override def provider: ActorRefProvider = {
    probe.asInstanceOf[InternalRecipientRef[_]].provider
  }

  // impl InternalRecipientRef
  def isTerminated: Boolean = {
    probe.asInstanceOf[InternalRecipientRef[_]].isTerminated
  }

  override def toString: String = s"TestEntityRef($entityId)"

  override private[pekko] def asJava: EntityRef[M] = this
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import java.util

import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.Attributes.InputBuffer
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage._

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ActorRefBackpressureSinkStage[In](
    ref: ActorRef,
    messageAdapter: ActorRef => In => Any,
    onInitMessage: ActorRef => Any,
    ackMessage: Option[Any],
    onCompleteMessage: Any,
    onFailureMessage: Throwable => Any)
    extends GraphStage[SinkShape[In]] {
  val in: Inlet[In] = Inlet[In]("ActorRefBackpressureSink.in")
  override def initialAttributes: Attributes = DefaultAttributes.actorRefWithBackpressureSink
  override val shape: SinkShape[In] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler {
      implicit def self: ActorRef = stageActor.ref

      private val maxBuffer = inheritedAttributes.get[InputBuffer](InputBuffer(16, 16)).max
      require(maxBuffer > 0, "Buffer size must be greater than 0")

      private val buffer: util.Deque[In] = new util.ArrayDeque[In]()
      private var acknowledgementReceived = false
      private var completeReceived = false
      private var completionSignalled = false

      private def receive(evt: (ActorRef, Any)): Unit = {
        evt._2 match {
          case Terminated(`ref`) => completeStage()
          case ackMsg if ackMessage.isEmpty || ackMessage.contains(ackMsg) =>
            if (buffer.isEmpty) {
              acknowledgementReceived = true
              if (completeReceived) finish()
            } else {
              // onPush might have filled the buffer up and
              // stopped pulling, so we pull here
              if (buffer.size() == maxBuffer) tryPull(in)
              dequeueAndSend()
            }
          case _ => // ignore all other messages
        }
      }

      override def preStart(): Unit = {
        setKeepGoing(true)
        getStageActor(receive).watch(ref)
        ref ! onInitMessage(self)
        pull(in)
      }

      private def dequeueAndSend(): Unit = {
        ref ! messageAdapter(self)(buffer.poll())
      }

      private def finish(): Unit = {
        ref ! onCompleteMessage
        completionSignalled = true
        completeStage()
      }

      def onPush(): Unit = {
        buffer.offer(grab(in))
        if (acknowledgementReceived) {
          dequeueAndSend()
          acknowledgementReceived = false
        }
        if (buffer.size() < maxBuffer) pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (buffer.isEmpty) finish()
        else completeReceived = true
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        ref ! onFailureMessage(ex)
        completionSignalled = true
        failStage(ex)
      }

      override def postStop(): Unit = {
        if (!completionSignalled) {
          ref ! onFailureMessage(new AbruptStageTerminationException(this))
        }
      }

      setHandler(in, this)
    }

  override def toString = "ActorRefBackpressureSink"
}
